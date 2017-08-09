/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "NetUtils"

#include "jni.h"
#include "JNIHelp.h"
#include "NetdClient.h"
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <linux/filter.h>
#include <linux/if.h>
#include <linux/if_arp.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <net/if_ether.h>
#include <netinet/icmp6.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include <netinet/udp.h>
#include <cutils/properties.h>
#include <fcntl.h>

#include "core_jni_helpers.h"

extern "C" {
int ifc_enable(const char *ifname);
int ifc_disable(const char *ifname);


/// M: support reset socket connections by the UID of process
int ifc_reset_connection_by_uid(int uid, int err);

int PPPOE_stop(const char *interface);
int PPPOE_do_request(const char *interface, int timeout_sec, const char *usr, const char *passwd, int interval, int failure, int mtu, int mru, int mss, char* iplocal, char* ipremote, char* gateway, char* dns1, char* dns2, char * ppplinkname);
char *PPPOE_get_errmsg();

int dhcpv6_PD_request(const char *interface, char *prefix, uint32_t *lease);
int dhcpv6_PD_renew(const char *interface, char *prefix, uint32_t *lease);
int dhcpv6_PD_stop(const char *interface);
char *PD_get_errmsg();
}

#define NETUTILS_PKG_NAME "android/net/NetworkUtils"

namespace android {

static const uint16_t kDhcpClientPort = 68;

/*
 * The following remembers the jfieldID's of the fields
 * of the DhcpInfo Java object, so that we don't have
 * to look them up every time.
 */
static struct fieldIds {
    jmethodID clear;
    jmethodID setIpAddress;
    jmethodID setGateway;
    jmethodID addDns;
    jmethodID setDomains;
    jmethodID setServerAddress;
    jmethodID setLeaseDuration;
    jmethodID setVendorInfo;
} dhcpResultsFieldIds;

static void android_net_utils_attachDhcpFilter(JNIEnv *env, jobject clazz, jobject javaFd)
{
    uint32_t ip_offset = sizeof(ether_header);
    uint32_t proto_offset = ip_offset + offsetof(iphdr, protocol);
    uint32_t flags_offset = ip_offset + offsetof(iphdr, frag_off);
    uint32_t dport_indirect_offset = ip_offset + offsetof(udphdr, dest);
    struct sock_filter filter_code[] = {
        // Check the protocol is UDP.
        BPF_STMT(BPF_LD  | BPF_B   | BPF_ABS,  proto_offset),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K,    IPPROTO_UDP, 0, 6),

        // Check this is not a fragment.
        BPF_STMT(BPF_LD  | BPF_H    | BPF_ABS, flags_offset),
        BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K,   0x1fff, 4, 0),

        // Get the IP header length.
        BPF_STMT(BPF_LDX | BPF_B    | BPF_MSH, ip_offset),

        // Check the destination port.
        BPF_STMT(BPF_LD  | BPF_H    | BPF_IND, dport_indirect_offset),
        BPF_JUMP(BPF_JMP | BPF_JEQ  | BPF_K,   kDhcpClientPort, 0, 1),

        // Accept or reject.
        BPF_STMT(BPF_RET | BPF_K,              0xffff),
        BPF_STMT(BPF_RET | BPF_K,              0)
    };
    struct sock_fprog filter = {
        sizeof(filter_code) / sizeof(filter_code[0]),
        filter_code,
    };

    int fd = jniGetFDFromFileDescriptor(env, javaFd);
    if (setsockopt(fd, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter)) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(SO_ATTACH_FILTER): %s", strerror(errno));
    }
}

static void android_net_utils_attachRaFilter(JNIEnv *env, jobject clazz, jobject javaFd,
        jint hardwareAddressType)
{
    if (hardwareAddressType != ARPHRD_ETHER) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "attachRaFilter only supports ARPHRD_ETHER");
        return;
    }

    uint32_t ipv6_offset = sizeof(ether_header);
    uint32_t ipv6_next_header_offset = ipv6_offset + offsetof(ip6_hdr, ip6_nxt);
    uint32_t icmp6_offset = ipv6_offset + sizeof(ip6_hdr);
    uint32_t icmp6_type_offset = icmp6_offset + offsetof(icmp6_hdr, icmp6_type);
    struct sock_filter filter_code[] = {
        // Check IPv6 Next Header is ICMPv6.
        BPF_STMT(BPF_LD  | BPF_B   | BPF_ABS,  ipv6_next_header_offset),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K,    IPPROTO_ICMPV6, 0, 3),

        // Check ICMPv6 type is Router Advertisement.
        BPF_STMT(BPF_LD  | BPF_B   | BPF_ABS,  icmp6_type_offset),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K,    ND_ROUTER_ADVERT, 0, 1),

        // Accept or reject.
        BPF_STMT(BPF_RET | BPF_K,              0xffff),
        BPF_STMT(BPF_RET | BPF_K,              0)
    };
    struct sock_fprog filter = {
        sizeof(filter_code) / sizeof(filter_code[0]),
        filter_code,
    };

    int fd = jniGetFDFromFileDescriptor(env, javaFd);
    if (setsockopt(fd, SOL_SOCKET, SO_ATTACH_FILTER, &filter, sizeof(filter)) != 0) {
        jniThrowExceptionFmt(env, "java/net/SocketException",
                "setsockopt(SO_ATTACH_FILTER): %s", strerror(errno));
    }
}

static jboolean android_net_utils_bindProcessToNetwork(JNIEnv *env, jobject thiz, jint netId)
{
    return (jboolean) !setNetworkForProcess(netId);
}

static jint android_net_utils_getBoundNetworkForProcess(JNIEnv *env, jobject thiz)
{
    return getNetworkForProcess();
}

static jboolean android_net_utils_bindProcessToNetworkForHostResolution(JNIEnv *env, jobject thiz,
        jint netId)
{
    return (jboolean) !setNetworkForResolv(netId);
}

static jint android_net_utils_bindSocketToNetwork(JNIEnv *env, jobject thiz, jint socket,
        jint netId)
{
    return setNetworkForSocket(netId, socket);
}

static jboolean android_net_utils_protectFromVpn(JNIEnv *env, jobject thiz, jint socket)
{
    return (jboolean) !protectFromVpn(socket);
}


static jboolean android_net_utils_queryUserAccess(JNIEnv *env, jobject thiz, jint uid, jint netId)
{
    return (jboolean) !queryUserAccess(uid, netId);
}


// M:
static jint android_net_utils_runPPPOE(JNIEnv* env, jobject clazz, jstring ifname, jint timeout, jstring usr, jstring passwd, jint interval, jint failure, jint mtu, jint mru, jint mss,jobject dhcpResults)
{
    int result;
    char iplocal[PROPERTY_VALUE_MAX];
    char ipremote[PROPERTY_VALUE_MAX];
    char gateway[PROPERTY_VALUE_MAX];
    char    dns1[PROPERTY_VALUE_MAX];
    char    dns2[PROPERTY_VALUE_MAX];
    char  ppplinkname[PROPERTY_VALUE_MAX];
    const char *nameStr = env->GetStringUTFChars(ifname, NULL);
    const char *usrStr = env->GetStringUTFChars(usr, NULL);
    const char *passwdStr = env->GetStringUTFChars(passwd, NULL);

    result = ::PPPOE_do_request(nameStr, timeout, usrStr, passwdStr, interval, failure, mtu, mru, mss, iplocal, ipremote, gateway, dns1, dns2, ppplinkname);
    env->ReleaseStringUTFChars(ifname, nameStr);
    env->ReleaseStringUTFChars(usr, usrStr);
    env->ReleaseStringUTFChars(passwd, passwdStr);

     if (result == 0) {
         env->CallVoidMethod(dhcpResults, dhcpResultsFieldIds.clear);

         // set the linkAddress
         // dhcpResults->addLinkAddress(inetAddress, prefixLength)
         result = env->CallBooleanMethod(dhcpResults, dhcpResultsFieldIds.setIpAddress,
                 env->NewStringUTF(iplocal), 32); // FIXME: we don't have prefixLength here, so use 32 instead, is it good enough?

         if (result == 0) {
             // set the gateway
             // dhcpResults->addGateway(gateway)
             result = env->CallBooleanMethod(dhcpResults,
                     dhcpResultsFieldIds.setGateway, env->NewStringUTF(gateway));
         }

         //env->SetObjectField(info, dhcpInfoInternalFieldIds.dns1, env->NewStringUTF(dns1));
         if (result == 0) {
             // dhcpResults->addDns(new InetAddress(dns1))
             result = env->CallBooleanMethod(dhcpResults,
                     dhcpResultsFieldIds.addDns, env->NewStringUTF(dns1));
         }

         //env->SetObjectField(info, dhcpInfoInternalFieldIds.dns2, env->NewStringUTF(dns2));
         if (result == 0) {
             result = env->CallBooleanMethod(dhcpResults,
                     dhcpResultsFieldIds.addDns, env->NewStringUTF(dns2));
         }

         // env->SetObjectField(info, dhcpInfoInternalFieldIds.interfaceName, env->NewStringUTF(ppplinkname));
         // if (result == 0) {
         //    env->CallVoidMethod(dhcpResults,
         //            dhcpResultsFieldIds.setPpplinkname, env->NewStringUTF(ppplinkname));
         // }
     }

     return result;
}

static jboolean android_net_utils_stopPPPOE(JNIEnv* env, jobject clazz, jstring ifname)
{
    int result;

    const char *nameStr = env->GetStringUTFChars(ifname, NULL);
    result = ::PPPOE_stop(nameStr);
    env->ReleaseStringUTFChars(ifname, nameStr);

    return (jboolean)(result == 0);
}

static jstring android_net_utils_getPPPOEError(JNIEnv* env, jobject clazz)
{
    return env->NewStringUTF(::PPPOE_get_errmsg());
}

/** M: support reset socket connections by the uid of process */
static jint android_net_utils_resetConnectionByUid(JNIEnv* env, jobject clazz, jint uid)
{
    int etimeout = 110;    /* ETIMEDOUT */
    int result = ::ifc_reset_connection_by_uid(uid, etimeout);
    ALOGD("android_net_utils_resetConnectionByUid:%d", result);
    return (jint)result;
}
/** @} */

/** M: support reset socket connections by the uid of process with special error number */
static jint android_net_utils_resetConnectionByUid_err(JNIEnv* env, jobject clazz, jint uid, jint err)
{
    int result = ::ifc_reset_connection_by_uid(uid, err);
    ALOGE("android_net_utils_resetConnectionByUid_err:%d", result);
    return (jint)result;
}
/** @} */

static jint android_net_utils_getRaFlags(JNIEnv* env, jobject clazz, jstring ifname) {
    int result, len, fd;
    char filename[64];
    char flags;
    const char *nameStr = env->GetStringUTFChars(ifname, NULL);

    snprintf(filename, sizeof(filename), "/proc/sys/net/ipv6/conf/%s/ra_info_flag", nameStr);
    fd = open(filename, O_RDONLY);
    if (fd < 0) {
        ALOGE("Can't open %s: %s", filename, strerror(errno));
        result = -1;
    } else {
        len = read(fd, &flags, 1);
        if (len < 0) {
            ALOGE("Can't read %s: %s", filename, strerror(errno));
            result = -2;
        } else {
            if (flags >= '0' && flags <= '4') {
                result = (int)(flags - '0');
                ALOGD("read:ra_info_flag=%c, result=%d\n", flags, result);
            } else {
                ALOGE("read:ra_info_flag=0x%x\n", flags);
                result = -3;
            }
        }
        close(fd);
    }

    env->ReleaseStringUTFChars(ifname, nameStr);
    return (jint)result;
}
/*JNI of PD*/
static jboolean android_net_utils_runPDCommon(JNIEnv* env, jobject clazz, jstring ifname, jobject dhcpResults, bool renew)
{
    int result;
    char  prefix[PROPERTY_VALUE_MAX];
    uint32_t lease;

    const char *nameStr = env->GetStringUTFChars(ifname, NULL);
    if (nameStr == NULL) return (jboolean)false;

    if (renew) {
        result = ::dhcpv6_PD_renew(nameStr, prefix, &lease);
    } else {
        result = ::dhcpv6_PD_request(nameStr, prefix, &lease);
    }

    env->ReleaseStringUTFChars(ifname, nameStr);

    if (result == 0) {
        env->CallVoidMethod(dhcpResults, dhcpResultsFieldIds.clear);

        // set mIfaceName
        // dhcpResults->setInterfaceName(ifname)
        //env->CallVoidMethod(dhcpResults, dhcpResultsFieldIds.setInterfaceName, ifname);

        // set the linkAddress
        //env->SetObjectField(info, dhcpInfoInternalFieldIds.ipaddress, env->NewStringUTF(prefix));
        result = env->CallBooleanMethod(dhcpResults, dhcpResultsFieldIds.setIpAddress,
                env->NewStringUTF(prefix), 64);

        //env->SetIntField(info, dhcpInfoInternalFieldIds.leaseDuration, lease);
        if (result == 0) {
            // dhcpResults->setLeaseDuration(lease)
            env->CallVoidMethod(dhcpResults,
                    dhcpResultsFieldIds.setLeaseDuration, lease);
        }
    }

    return (jboolean)(result == 0);
}

static jboolean android_net_utils_runPD(JNIEnv* env, jobject clazz, jstring ifname, jobject info)
{
    return android_net_utils_runPDCommon(env, clazz, ifname, info, false);
}

static jboolean android_net_utils_runPDRenew(JNIEnv* env, jobject clazz, jstring ifname, jobject info)
{
    return android_net_utils_runPDCommon(env, clazz, ifname, info, true);
}

static jboolean android_net_utils_stopPD(JNIEnv* env, jobject clazz, jstring ifname)
{
    int result;

    const char *nameStr = env->GetStringUTFChars(ifname, NULL);
    result = ::dhcpv6_PD_stop(nameStr);
    env->ReleaseStringUTFChars(ifname, nameStr);
    return (jboolean)(result == 0);
}

static jstring android_net_utils_getPDError(JNIEnv* env, jobject clazz)
{
    return env->NewStringUTF(::PD_get_errmsg());
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static const JNINativeMethod gNetworkUtilMethods[] = {
    /* name, signature, funcPtr */
    { "bindProcessToNetwork", "(I)Z", (void*) android_net_utils_bindProcessToNetwork },
    { "getBoundNetworkForProcess", "()I", (void*) android_net_utils_getBoundNetworkForProcess },
    { "bindProcessToNetworkForHostResolution", "(I)Z", (void*) android_net_utils_bindProcessToNetworkForHostResolution },
    { "bindSocketToNetwork", "(II)I", (void*) android_net_utils_bindSocketToNetwork },
    { "protectFromVpn", "(I)Z", (void*)android_net_utils_protectFromVpn },
    { "queryUserAccess", "(II)Z", (void*)android_net_utils_queryUserAccess },
    { "attachDhcpFilter", "(Ljava/io/FileDescriptor;)V", (void*) android_net_utils_attachDhcpFilter },
    { "attachRaFilter", "(Ljava/io/FileDescriptor;I)V", (void*) android_net_utils_attachRaFilter },
    // M:
    {"runPPPOE", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;IIIIILandroid/net/DhcpResults;)I", (void*)android_net_utils_runPPPOE },
    {"stopPPPOE", "(Ljava/lang/String;)Z", (void*)android_net_utils_stopPPPOE },
    { "getPPPOEError", "()Ljava/lang/String;", (void*) android_net_utils_getPPPOEError },
    /// M: MTK network utility functions
    { "resetConnectionByUidErrNum", "(II)I",  (void *)android_net_utils_resetConnectionByUid_err },
    { "resetConnectionByUid", "(I)I",  (void *)android_net_utils_resetConnectionByUid },
    { "getRaFlags", "(Ljava/lang/String;)I",  (void *)android_net_utils_getRaFlags },
    /// M: add PD corresponding JNI declaration
    { "runDhcpv6PD", "(Ljava/lang/String;Landroid/net/DhcpResults;)Z",  (void *)android_net_utils_runPD },
    { "runDhcpv6PDRenew", "(Ljava/lang/String;Landroid/net/DhcpResults;)Z",  (void *)android_net_utils_runPDRenew },
    { "stopDhcpv6PD", "(Ljava/lang/String;)Z",  (void *)android_net_utils_stopPD },
    { "getDhcpv6PDError", "()Ljava/lang/String;", (void*)android_net_utils_getPDError },
};

int register_android_net_NetworkUtils(JNIEnv* env)
{
    return RegisterMethodsOrDie(env, NETUTILS_PKG_NAME, gNetworkUtilMethods,
                                NELEM(gNetworkUtilMethods));
}

}; // namespace android
