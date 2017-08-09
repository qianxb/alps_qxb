package com.mediatek.usp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;


import static com.android.internal.util.Preconditions.checkNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Usp Service.
 */
public class UspServiceImpl extends IUspService.Stub {

    // TO DO: Minimize class elements
    private final String TAG = "UspServiceImpl";
    private final boolean DEBUG = true;
    private final boolean TESTING_PURPOSE = true; // remove or set false when given to customer
    private Context mContext;
    private int mConfigState = UspManager.SRV_CONFIG_STATE_INIT;
    private PackageManager mPm;
    private AlertDialog mDialog;
    private static final String SYSTEM_PATH = "/system/usp";
    private static final String CUSTOM_PATH = "/custom/usp";
    private static final String VENDOR_PATH = "/vendor/usp";
    private static final String USP_INFO_FILE = "usp-info.txt";
    private static Map<String, String> sOperatorMapInfo;
    static {
        sOperatorMapInfo = new HashMap<String, String>();
        sOperatorMapInfo.put("OP01", "CMCC");
        sOperatorMapInfo.put("OP02", "CU");
        sOperatorMapInfo.put("OP03", "Orange");
        sOperatorMapInfo.put("OP05", "TMO EU");
        sOperatorMapInfo.put("OP06", "Vodafone");
        sOperatorMapInfo.put("OP07", "AT&T");
        sOperatorMapInfo.put("OP08", "TMO US");
        sOperatorMapInfo.put("OP09", "CT");
        sOperatorMapInfo.put("OP11", "H3G");
        sOperatorMapInfo.put("OP12", "Verizon");
        sOperatorMapInfo.put("OP15", "Telefonica");
        sOperatorMapInfo.put("OP16", "EE");
        sOperatorMapInfo.put("OP17", "DoCoMo");
        sOperatorMapInfo.put("OP18", "Reliance");
        sOperatorMapInfo.put("OP19", "Telstra");
        sOperatorMapInfo.put("OP20", "Sprint");
        sOperatorMapInfo.put("OP50", "Softbank");
        sOperatorMapInfo.put("OP100", "CSL");
        sOperatorMapInfo.put("OP101", "PCCW");
        sOperatorMapInfo.put("OP102", "SMT");
        sOperatorMapInfo.put("OP103", "SingTel");
        sOperatorMapInfo.put("OP104", "Starhub");
        sOperatorMapInfo.put("OP105", "AMX");
        sOperatorMapInfo.put("OP106", "3HK");
        sOperatorMapInfo.put("OP107", "SFR");
        sOperatorMapInfo.put("OP108", "TWN");
        sOperatorMapInfo.put("OP109", "CHT");
        sOperatorMapInfo.put("OP110", "FET");
        sOperatorMapInfo.put("OP112", "Telcel");
        sOperatorMapInfo.put("OP113", "Beeline");
        sOperatorMapInfo.put("OP114", "KT");
        sOperatorMapInfo.put("OP115", "SKT");
        sOperatorMapInfo.put("OP116", "U+");
        sOperatorMapInfo.put("OP117", "Smartfren");
        sOperatorMapInfo.put("OP118", "YTL");
        sOperatorMapInfo.put("OP119", "Natcom");
        sOperatorMapInfo.put("OP120", "Claro");
        sOperatorMapInfo.put("OP121", "Bell");
        sOperatorMapInfo.put("OP122", "AIS");
        sOperatorMapInfo.put("OP124", "APTG");
        sOperatorMapInfo.put("OP125", "DTAC");
        sOperatorMapInfo.put("OP126", "Avea");
        sOperatorMapInfo.put("OP127", "Megafon");
        sOperatorMapInfo.put("OP128", "DNA");
        sOperatorMapInfo.put("OP129", "KDDI");
        sOperatorMapInfo.put("OP130", "TIM");
        sOperatorMapInfo.put("OP131", "TrueMove");
        sOperatorMapInfo.put("OP1001", "Ericsson");
    }

    private List<String> mPendingEnableDisableReq = new ArrayList<String>();
    private static final String PROP_GSM_SIM_OPERATOR_NUMERIC = "gsm.sim.operator.numeric";
    private static final String PROP_PERSIST_BOOTANIM_MNC = "persist.bootanim.mnc";
    // This PROP_CXP_CONFIG_CTRL is used internally by service to keep some control flags
    // one for first boot, second for first valid sim switch
    private static final String PROP_CXP_CONFIG_CTRL = "persist.mtk_usp_cfg_ctrl";
    private static final int MAX_AT_CMD_RESPONSE = 2048;

    private static final int PROP_CFG_CTRL_FLAG_NOT_FIRST_BOOT = 1;
    private static final int PROP_CFG_CTRL_FLAG_FIRST_SIM_ONLY_DONE = 2;
    private static final int PROP_CFG_CTRL_FLAG_CONFIG_STATE_INVALID = 3;
    private static final int PROP_CFG_CTRL_FLAG_POPUP_HANDLED = 4;

    /**
     * Api to freeze frame.
     * @return int operation result
     */
    public static native int freezeFrame();
    /**
     * Api to unfreeze frame.
     * @return int operation result
     */
    public static native int unfreezeFrame();

    private MyHandler mUiHandler;

    static {
        System.loadLibrary("usp_native");
    }

    /**
     * constructor of Usp service.
     * @param context from system server
     */
    public UspServiceImpl(Context context) {
        Log.d(TAG, "UspServiceImpl");
        mContext = checkNotNull(context, "missing Context");
        //we always register SIM change as user may swap it
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        mContext.registerReceiver(mIntentReceiver, filter);
        mUiHandler = new MyHandler();
    }

    /**
     *
     * Start function for service.
     */
    public void start() {
        Log.d(TAG, "start");
        if (getConfigCtrlFlag(PROP_CFG_CTRL_FLAG_CONFIG_STATE_INVALID, null)) {
            // here only comes when configiration is invalid
            String opPack = SystemProperties.get("persist.operator.optr");
            if (!isOperatorValid(opPack)) {
                Log.d(TAG, "Operator pack not valid: " + opPack);
                setConfigCtrlFlag(PROP_CFG_CTRL_FLAG_CONFIG_STATE_INVALID, false, null);
            } else {
                startConfiguringOpPack(opPack, true);
            }
        }
        if (!getConfigCtrlFlag(PROP_CFG_CTRL_FLAG_NOT_FIRST_BOOT, null)) {
            firstBootConfigure();
            setConfigCtrlFlag(PROP_CFG_CTRL_FLAG_NOT_FIRST_BOOT, true, null);
        } else {
            String mccMnc = readMCCMNCFromProperty();
            if (mccMnc.length() < 5) {
                Log.d(TAG, "Invalid mccMnc " + mccMnc);
                return;
            }
            String optr = getOperatorPackForSim(mccMnc);
            handleSwitchOperator(optr);
        }
    }

    void firstBootConfigure() {
        Log.d(TAG, "firstBootConfigure");
        // currently we are assuming that followings are default from load,
        // so no action required for them
        //   1. system properties from cip-build.prop
        //   2. sbp md from cip-build.prop
        //   3. lk / boot logo from default inbuilt behaviour
        //   4. install/uninstall apks from default behaviour*(TBD)

        // first config is either OM config or OP config
        // here only configure for enable/disable apks
        String optr = SystemProperties.get("persist.operator.optr");
        if (optr == null || optr.length() <= 0) {
            Log.d(TAG, "firstBootConfigure: OM config");
            enabledDisableApps("OM");
        } else {
            Log.d(TAG, "firstBootConfigure: OP config" + optr);
             enabledDisableApps(optr);
        }
    }


    boolean getConfigCtrlFlag(int prop, String optr) {
        int propValue = SystemProperties.getInt(PROP_CXP_CONFIG_CTRL, 0);
        switch (prop) {
        case PROP_CFG_CTRL_FLAG_NOT_FIRST_BOOT:
            return ((propValue & 0x1) == 1);
        case PROP_CFG_CTRL_FLAG_FIRST_SIM_ONLY_DONE:
            return ((propValue & 0x2) == 2);
        case PROP_CFG_CTRL_FLAG_CONFIG_STATE_INVALID:
            return ((propValue & 0x4) == 4);
        case PROP_CFG_CTRL_FLAG_POPUP_HANDLED:
            int numStored = (propValue & 0xFFFF0000) >> 16;
            String numOptrStr = optr.substring(2, optr.length());
            try {
                int numOptr = Integer.parseInt(numOptrStr);
                Log.d(TAG, "saved: " + numStored + "cur: " + numOptr);
                if (numOptr == numStored) {
                    return true;
                }
            } catch (NumberFormatException e) {
                Log.d(TAG, "getConfigCtrlFlag: 2" + e.toString());
            }
            break;
        }
        return false;
    }

    void setConfigCtrlFlag(int prop, boolean flag, String optr) {
        int propValue = SystemProperties.getInt(PROP_CXP_CONFIG_CTRL, 0);
        switch (prop) {
        case PROP_CFG_CTRL_FLAG_NOT_FIRST_BOOT:
            propValue &= 0xFFFFFFFE; //reset this with 1st bit
            if (flag) {
                propValue |= 0x1;
            }
            break;
        case PROP_CFG_CTRL_FLAG_FIRST_SIM_ONLY_DONE:
            propValue &= 0xFFFFFFFD; //reset this with 2nd bit
            if (flag) {
                propValue |= 0x2;
            }
            break;
        case PROP_CFG_CTRL_FLAG_CONFIG_STATE_INVALID:
            propValue &= 0xFFFFFFFB; //reset this with 3st bit
            if (flag) {
                propValue |= 0x4;
            }
            break;
        case PROP_CFG_CTRL_FLAG_POPUP_HANDLED:
            if (optr != null && optr.length() >= 3) {
                String numStr = optr.substring(2, optr.length());
                try {
                    int num = Integer.parseInt(numStr);
                    propValue &= 0x0000FFFF; //reset this with last 16 bits
                    propValue |= (num << 16);
                } catch (NumberFormatException e) {
                    Log.d(TAG, "setConfigCtrlFlag: 2" + e.toString());
                }
            }
            break;
        }
        SystemProperties.set(PROP_CXP_CONFIG_CTRL, "" + propValue);
    }

    boolean isFirstValidSimConfigured() {
        String simSwitchMode = SystemProperties.get("ro.mtk_cxp_switch_mode");
        if (simSwitchMode != null && simSwitchMode.equals("2")
                && getConfigCtrlFlag(PROP_CFG_CTRL_FLAG_FIRST_SIM_ONLY_DONE, null)) {
            return true;
        }
        return false;
    }

    void handleSwitchOperator(String optr) {
        if (isFirstValidSimConfigured()) {
            Log.d(TAG, "isFirstValidSimConfigured: true");
            return;
        }
        if (!isOperatorValid(optr)) {
            Log.d(TAG, "Operator pack not valid: " + optr);
            return;
        }
        if (optr.equals(getActiveOpPack())) {
            Log.d(TAG, "same active operator: " + optr);
            // add first valid sim switch mod set, still as sim detected of same optr
            String simSwitchMode = SystemProperties.get("ro.mtk_cxp_switch_mode");
            if (simSwitchMode != null && simSwitchMode.equals("2")) {
                setConfigCtrlFlag(PROP_CFG_CTRL_FLAG_FIRST_SIM_ONLY_DONE, true, null);
                Log.d(TAG, "set first valid sim configured");
            }
            return;
        }
        if (mConfigState != UspManager.SRV_CONFIG_STATE_WAIT) {
            // add for first valid sim switch mod set, it will execute only for first time
            String simSwitchMode = SystemProperties.get("ro.mtk_cxp_switch_mode");
            if (simSwitchMode != null && simSwitchMode.equals("2")) {
                setConfigCtrlFlag(PROP_CFG_CTRL_FLAG_FIRST_SIM_ONLY_DONE, true, null);
                Log.d(TAG, "set first valid sim configured");
                startConfiguringOpPack(optr, false);
            } else if (!getConfigCtrlFlag(PROP_CFG_CTRL_FLAG_POPUP_HANDLED, optr)){
                new UspUserDialog(optr).showDialog();
                setConfigCtrlFlag(PROP_CFG_CTRL_FLAG_POPUP_HANDLED, true, optr);
          }
        }
    }

    @Override
    public String getActiveOpPack() {
        String optr = SystemProperties.get("persist.operator.optr");
        return optr;
    }

    @Override
    public String getOpPackFromSimInfo(String mccMnc) {
        if (mccMnc != null && mccMnc.length() > 0) {
            return getOperatorPackForSim(mccMnc);
        }
        return "";
    }

    @Override
    public void setOpPackActive(String opPack) {
        Log.i(TAG, "setOpPackActive" + opPack);
        String simSwitchMode = SystemProperties.get("ro.mtk_cxp_switch_mode");
        if (simSwitchMode != null && simSwitchMode.equals("2")) {
            Log.d(TAG, "First valid sim is enabled: ");
            return;
        }
        if (!isOperatorValid(opPack)) {
            Log.d(TAG, "Operator pack not valid: " + opPack);
            return;
        }
        if (opPack.equals(getActiveOpPack())) {
            Log.d(TAG, "same active operator: " + opPack);
            return;
        }
        if (mConfigState != UspManager.SRV_CONFIG_STATE_WAIT) {
            startConfiguringOpPack(opPack, false);
        }
    }

    boolean isOperatorValid(String optr) {
        if (optr == null || optr.length() <= 0) {
            Log.d(TAG, "error in operator: " + optr);
            return false;
        } else if (getAllOpList().contains(optr) == true) {
            // check for operator pack exists
            return true;
        }
        Log.d(TAG, "Operator not found in all op pack list");
        return false;
    }

    List<String> getAllOpList() {
        String ops = getRegionalOpPack();
        List<String> opList = new ArrayList<String>();
        try {
            String[] opSplit = ops.split(" ");
            for (int count = 0; count < opSplit.length; ++count) {
                if (opSplit[count] != null && opSplit[count].length() > 0) {
                    int firstUnderscoreIndex = opSplit[count].indexOf("_");
                    opList.add(opSplit[count].substring(0, firstUnderscoreIndex));
                }
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "illegal string passed to splitString: " + e.toString());
        }
        return opList;
    }

    @Override
    public Map<String, String> getAllOpPackList() {
        String ops = getRegionalOpPack();
        Map<String, String> operatorMapInfo = new HashMap<String, String>();
        try {
            String[] opSplit = ops.split(" ");
            for (int count = 0; count < opSplit.length; ++count) {
                if (opSplit[count] != null && opSplit[count].length() > 0) {
                    int firstUnderscoreIndex = opSplit[count].indexOf("_");
                    operatorMapInfo.put(opSplit[count].substring(0, firstUnderscoreIndex),
                            getOperatorNameFromPack(opSplit[count].substring(0,
                                    firstUnderscoreIndex)));
                }
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "illegal string passed to splitString: " + e.toString());
        }
        return operatorMapInfo;
    }

    private String getOperatorPackForSim(String mccMnc) {
        // fetch operator from framework res
        try {
            int mccMncNum = Integer.parseInt(mccMnc);
            String[] operatorList = Resources.getSystem().getStringArray(
                com.mediatek.internal.R.array.operator_map_list);
            for (String item : operatorList) {
               String[] opSplit = item.split("\\s*,\\s*");
               if (mccMncNum >= Integer.parseInt(opSplit[0]) &&
                       mccMncNum <= Integer.parseInt(opSplit[1])) {
                   Log.d(TAG, "getOperatorPackForSim optr: " + opSplit[2]);
                   return "OP" + opSplit[2];
               }
            }
        } catch (Resources.NotFoundException | IndexOutOfBoundsException |
                NumberFormatException e) {
            Log.e(TAG, "getOperatorPackForSim Exception: " + e.toString());
        }
        Log.d(TAG, "getOperatorPackForSim optr NOT FOUND");
        return "";
    }

    private void startConfiguringOpPack(String opPack, boolean isReconfig) {
        Log.d(TAG, "startConfiguringOpPack: " + opPack);
        mUiHandler.sendMessage(mUiHandler.obtainMessage(MyHandler.REBOOT_DIALOG,
                (Boolean) isReconfig));
        // set state as wait
        mConfigState = UspManager.SRV_CONFIG_STATE_WAIT;

        runningConfigurationTask(opPack);
    }
    private void runningConfigurationTask(String opPack) {
        Log.d(TAG, "runningConfigurationTask " + opPack);
        Thread th = new Thread() {
            @Override
            public void run() {
                setConfigCtrlFlag(PROP_CFG_CTRL_FLAG_CONFIG_STATE_INVALID, true, null);

                // send Modem AT+EPOF cmd
                sendMdPowerOffCmd();
                // Set current OP property
                setProperties(opPack);

                // Set for SBP Modem
                setMdSbpProperty(opPack);
                // Enable-disable op packages apps, plugins, resource overlays
                enabledDisableApps(opPack);

                freeze();
            }
        };
        th.start();
    }

    private String sendMdPowerOffCmd() {
        String atCmd = new String("AT+EPOF\r\n");
        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) mContext.getSystemService(mContext.TELEPHONY_SERVICE);
            byte[] rawData = atCmd.getBytes();
            byte[] cmdByte = new byte[rawData.length + 1];
            byte[] cmdResp = new byte[MAX_AT_CMD_RESPONSE];
            System.arraycopy(rawData, 0, cmdByte, 0, rawData.length);
            cmdByte[cmdByte.length - 1] = 0;
            Log.d(TAG, "sendMdPowerOffCmd:" + atCmd);
            int ret = telephonyManager.invokeOemRilRequestRaw(cmdByte, cmdResp);
            if (ret != -1) {
                cmdResp[ret] = 0;
                return new String(cmdResp);
            }
        } catch (NullPointerException ee) {
                ee.printStackTrace();
        }
        return "";
    }

    private void showWaitingScreen(boolean isReconfig) {
        AlertDialog dialog = new AlertDialog(mContext) {
            // This dialog will consume all events coming in to
            // it, to avoid it trying to do things too early in boot.
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                return true;
            }
            @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
                return true;
            }
            @Override public boolean dispatchTouchEvent(MotionEvent ev) {
                return true;
            }
            @Override public boolean dispatchTrackballEvent(MotionEvent ev) {
                return true;
            }
            @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
                return true;
            }
            @Override public boolean dispatchPopulateAccessibilityEvent(
                    AccessibilityEvent event) {
                return true;
            }
        };
        if (isReconfig) {
            dialog.setMessage(mContext.getResources()
                    .getString(com.mediatek.internal.R.string.reconfig_dialog_message));
        } else {
            dialog.setMessage(mContext.getResources()
                    .getString(com.mediatek.internal.R.string.reboot_dialog_message));
        }
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY);
        dialog.getWindow().getAttributes().privateFlags |=
                WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        //dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
          //      | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        //dialog.getWindow().setDimAmount(1);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        dialog.getWindow().setAttributes(lp);
        dialog.show();
        Log.d(TAG, "showing WaitingScreen");
    }

    private void freeze() {
        Thread th = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(500);  //Delay of .5 seconds to allow buffer to flush before freezing it
                } catch (Exception e) {
                    Log.d(TAG, "when freeze sleep exception happened");
                }
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MyHandler.FREEZE_FRAME));
            }
        };
         th.start();
    }

    private void rebootAndroidSystem() {
        Thread th = new Thread() {
            @Override
            public void run() {
                /*try {
                    sleep(500);  //Delay of .5 seconds to alow buffer to flush before freezing it
                } catch (Exception e) {
                    Log.d(TAG, "when sleep1 exception happened");
                }
                mUiHandler.sendMessage(mUiHandler.obtainMessage(MyHandler.FREEZE_FRAME));*/
                try {
                    //sleep(10000);
                    //keep checking Pending Requests at every .5 seconds
                    for(int i = 0; i < 25; i++) {
                        if (!mPendingEnableDisableReq.isEmpty()) {
                            sleep(500);
                            if (i == 24) {
                                Log.e(TAG, "Enable Diable May Have Not Completed");
                                //Abort the process Here
                            }
                        } else {
                            mContext.unregisterReceiver(mEnableDisableRespReceiver);
                            Log.d(TAG, "All Enable Disable completed before " + i +
                                    "th Sleep, Now Going to Reboot");
                            Log.d(TAG, "Going to Reboot Android System");
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "when sleep exception happened");
                }
                mConfigState = UspManager.SRV_CONFIG_STATE_IDLE;
                //set optr in native sys env for boot logo and restart zygote from init.rc
                SystemProperties.set("persist.mtk_usp_native_start", "1");
            }
        };
        th.start();
    }

    /**
     * Api to freeze screen.
     */
    public void unfreezeScreen() {
        if (unfreezeFrame() < 0) {
            Log.e(TAG, "UNFREEZING FRAME FAILED.....WE ARE DEAD :(");
        }
    }

    protected BroadcastReceiver mEnableDisableRespReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String[] data = intent.getData().toString().split(":");
            String packageName = data[data.length-1];
            mPendingEnableDisableReq.remove(packageName);
            Log.d(TAG, "mEnableDisableRespReceiver, got response for package name="
                    + packageName);
            Log.d(TAG, "Dump mPendingEnableDisableReq List of Size:"
                    + mPendingEnableDisableReq.size() +
                    Arrays.toString(mPendingEnableDisableReq.toArray()));
            if (mPendingEnableDisableReq.isEmpty()) {
                Log.d(TAG, "mEnableDisableRespReceiver," +
                    "mPendingEnableDisableReq empty So Calling rebootAndroidSystem");
                rebootAndroidSystem();
            }
        }
    };

    protected BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        // Broadcast Action: The sim card state has changed.
        private static final String ACTION_SIM_STATE_CHANGED =
                "android.intent.action.SIM_STATE_CHANGED";
        // The extra data for broacasting intent INTENT_ICC_STATE_CHANGE
        private static final String INTENT_KEY_ICC_STATE = "ss";
        // LOADED means all ICC records, including IMSI, are loaded
        private static final String INTENT_VALUE_ICC_LOADED = "LOADED";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "BroadcastReceiver(), SIM state change, action=" + action);
            if (action != null && action.equals(ACTION_SIM_STATE_CHANGED)) {
                String newState = intent.getStringExtra(INTENT_KEY_ICC_STATE);
                Log.d(TAG, "BroadcastReceiver(), SIM state change, new state=" + newState);
                if (newState.equals(INTENT_VALUE_ICC_LOADED) &&
                        mConfigState != UspManager.SRV_CONFIG_STATE_WAIT) {
                    String mccMnc = readMCCMNCFromProperty();
                    if (mccMnc.length() < 5) {
                        Log.d(TAG, "Invalid mccMnc " + mccMnc);
                        return;
                    }
                    String optr = getOperatorPackForSim(mccMnc);
                    handleSwitchOperator(optr);
                }
            }
        }
    };

    private String readMCCMNCFromProperty() {
        // We are usig a temp process to verify any of the string mcc_mnc
        // by setting a value from property
        if (TESTING_PURPOSE == true) {
            String value = readMCCMNCFromPropertyForTesting();
            Log.d(TAG, "readMCCMNCFromPropertyForTesting " + value);
            return value;
        }
        // try bootanim property firstly
        String mccMnc = SystemProperties.get(PROP_PERSIST_BOOTANIM_MNC);
        if (mccMnc != null && mccMnc.length() > 4) {
            Log.d(TAG, "read mcc mnc property from boot anim: " + PROP_PERSIST_BOOTANIM_MNC);
            return mccMnc;
        }
        // failed to read bootanim property, then try other property
        mccMnc = SystemProperties.get(PROP_GSM_SIM_OPERATOR_NUMERIC);
        if (mccMnc != null && mccMnc.length() > 4) {
            Log.d(TAG, "read mcc mnc property from " + PROP_GSM_SIM_OPERATOR_NUMERIC);
            return mccMnc;
        }
        Log.d(TAG, "failed to read mcc mnc from property");
        return "";
    }

    private String readMCCMNCFromPropertyForTesting() {
        // We are usig a temp process to verify any of the string mcc_mnc
        // by setting a value from property
        String dummyMccMnc = SystemProperties.get("persist.simulate_cxp_sim");
        // try bootanim property firstly
        String mccMnc = SystemProperties.get(PROP_PERSIST_BOOTANIM_MNC);
        if (mccMnc != null && mccMnc.length() > 4) {
            Log.d(TAG, "read mcc mnc property from boot anim: " + PROP_PERSIST_BOOTANIM_MNC);
            return (dummyMccMnc != null && dummyMccMnc.length() > 4) ? dummyMccMnc : mccMnc;
        }
        // failed to read bootanim property, then try other property
        mccMnc = SystemProperties.get(PROP_GSM_SIM_OPERATOR_NUMERIC);
        if (mccMnc != null && mccMnc.length() > 4) {
            Log.d(TAG, "read mcc mnc property from " + PROP_GSM_SIM_OPERATOR_NUMERIC);
            return (dummyMccMnc != null && dummyMccMnc.length() > 4) ? dummyMccMnc : mccMnc;
        }
        Log.d(TAG, "failed to read mcc mnc from property");
        return "";
    }

    /**
    * UspUserDialog.
    */
    private class UspUserDialog implements DialogInterface.OnClickListener {
        private String mOptr;

        UspUserDialog(String optr) {
            mOptr = optr;
        }

        void showDialog() {
            String content = Resources.getSystem().getString(
                    com.mediatek.internal.R.string.usp_config_confirm);
            String operatorName = getOperatorNameFromPack(mOptr);
            StringBuilder message = new StringBuilder(
                    "[" + operatorName + "] " + content);
            mDialog = new AlertDialog.Builder(mContext)
                    .setMessage(message.toString())
                    .setPositiveButton(android.R.string.yes, this)
                    .setNegativeButton(android.R.string.no, this)
                    .create();
            mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
            mDialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mDialog.setCanceledOnTouchOutside(false);
            mDialog.show();
            Log.d(TAG, "showDialog " + mDialog);
        }

        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            if (DialogInterface.BUTTON_POSITIVE == whichButton) {
                Log.d(TAG, "Click for yes");
                startConfiguringOpPack(mOptr, false);
            }
            mDialog.dismiss();
        }
    }

    private String getOperatorNameFromPack(String optr) {
        /*if(sOperatorMapInfo.isEmpty()) {
            try {
                String[] operatorNameList = Resources.getSystem().getStringArray(
                    com.mediatek.internal.R.array.operator_name_list);
                for(String item : operatorNameList) {
                   String[] opSplit = item.split("=");
                   sOperatorMapInfo.put(opSplit[0], opSplit[1]);
                }
            } catch (Resources.NotFoundException | IndexOutOfBoundsException e) {
                Log.e(TAG, "illegal optr resource string: " + e.toString());
            }
        }*/
        if (sOperatorMapInfo.containsKey(optr)) {
            Log.d(TAG, "getOperatorNameFromPack for optr: " + optr);
            return sOperatorMapInfo.get(optr);
        }
        return new String("Unknown");
    }

    private void setMdSbpProperty(String optr) {
        String val = new String(optr.substring(2, optr.length()));
        Log.d(TAG, "setMdSbpProperty value: " + val);
        SystemProperties.set("persist.mtk_usp_md_sbp_code", val);
    }

    private void setProperties(String optr) {
        File customGlobalDir;
        if (new File(CUSTOM_PATH).exists()) {
            customGlobalDir = new File(CUSTOM_PATH);
        } else if (new File(VENDOR_PATH).exists()) {
            customGlobalDir = new File(VENDOR_PATH);
        } else {
            Log.e(TAG, "none of custom/usp or vendor/usp exists");
            return;
        }
        String propFileName = "usp-properties" + "-" + optr + ".txt";
        File customPropFile = new File(customGlobalDir, propFileName);
        List<String> opPropertyList = readFromFile(customPropFile);
        for (int i = 0; i < opPropertyList.size(); i++) {
            String key = getKey(opPropertyList.get(i).trim());
            String value = getValue(opPropertyList.get(i).trim());
            Log.d(TAG, "setting property " + key + "  TO  " + value);
            set(mContext, key, value);
        }
    }

    private String getRegionalOpPack() {
        Log.d(TAG, "getRegionalOpPack ");
        File customGlobalDir;
        if (new File(CUSTOM_PATH).exists()) {
            customGlobalDir = new File(CUSTOM_PATH);
        } else if (new File(VENDOR_PATH).exists()) {
            customGlobalDir = new File(VENDOR_PATH);
        } else {
            Log.e(TAG, "none of custom/usp or vendor/usp exists");
            return "";
        }
        String fileName = USP_INFO_FILE;
        File customFile = new File(customGlobalDir, fileName);
        List<String> data = readFromFile(customFile);
        for (int i = 0; i < data.size(); i++) {
            String key = getKey(data.get(i).trim());
            Log.d(TAG, "MTK_REGIONAL_OP_PACK = " + key);
            if (key.equals("MTK_REGIONAL_OP_PACK")) {
                String value = getValue(data.get(i).trim());
                Log.d(TAG, "MTK_REGIONAL_OP_PACK = " + value);
                return value;
            }
        }
        return "";
    }
    /**
     * Set the value for the given key.
     * @throws IllegalArgumentException if the key exceeds 32 characters
     * @throws IllegalArgumentException if the value exceeds 92 characters
     */
    private void set(Context context, String key, String val) throws IllegalArgumentException {
        try {
           SystemProperties.set(key, val);
        } catch (IllegalArgumentException iAE) {
             throw iAE;
        } catch (Exception e) {
             //TODO
        }
    }

    private void enabledDisableApps(String optr) {
        String isInstSupport = SystemProperties.get("ro.mtk_carrierexpress_inst_sup");
        if (isInstSupport != null && isInstSupport.equals("1")) {
            Log.d(TAG, "Install/uninstall apk is enabled");
            return;
        }
        File customGlobalDir;
        if (new File(CUSTOM_PATH).exists()) {
            customGlobalDir = new File(CUSTOM_PATH);
        } else if (new File(VENDOR_PATH).exists()) {
            customGlobalDir = new File(VENDOR_PATH);
        } else {
            Log.e(TAG, "none of custom/usp or vendor/usp exists");
            return;
        }
        String[] customGlobalFiles = customGlobalDir.list();
        String opFileName = "usp-packages" + "-" + optr + ".txt";
        String allFileName = "usp-packages" + "-" + "all" + ".txt";
        File customAllFile = new File(customGlobalDir, allFileName);
        File customOpFile = new File(customGlobalDir, opFileName);

        mPm = mContext.getPackageManager();
        List<String> allPackageList = readFromFile(customAllFile);
        Log.d(TAG, "enabledDisableApps ALL File First content" + allPackageList.get(0));
        List<String> opPackageList = readFromFile(customOpFile);
        //Register broadcast receiver for tracking enable-disable Status
        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        packageFilter.addDataScheme("package");
        mContext.registerReceiver(mEnableDisableRespReceiver, packageFilter);
        //Disable Apps Present in All but not in OP File, Don't Disable if Already Disabled
        for (int i = 0; i < allPackageList.size(); i++) {
            Log.d(TAG, allPackageList.get(i) + " not in OP File " +
                    !opPackageList.contains(allPackageList.get(i)) +
                    " EnabledState: " + getPackageEnabledState(allPackageList.get(i), false));
            if ((!opPackageList.contains(allPackageList.get(i))) &&
                    getPackageEnabledState(allPackageList.get(i), false)) {
                mPendingEnableDisableReq.add(allPackageList.get(i));
                //Log.d(TAG, "enabledDisableApps contains " + allPackageList.get(i));
            disableApps(allPackageList.get(i));
        }
        }
        //Enable Apps Present in All but not in OP File, Don't Enable if Already Enable
        for (int i = 0; i < opPackageList.size(); i++) {
            Log.d(TAG, opPackageList.get(i) + " EnabledState: "
                    + getPackageEnabledState(opPackageList.get(i), true));
            if (!getPackageEnabledState(opPackageList.get(i), true)) {
                mPendingEnableDisableReq.add(opPackageList.get(i));
            enableApps(opPackageList.get(i));
        }
    }
    }

    //Takes a defaultState boolean to handle packages which are not installed
    private boolean getPackageEnabledState(String packageName, boolean defaultState) {
        ApplicationInfo ai = null;
        try {
            ai = mPm.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "getPackageEnabledState, packageNotFound: " + packageName);
            return defaultState;
        }

        return ai.enabled;
    }


    private String getKey(String toBeSplit) {
        int assignmentIndex = toBeSplit.indexOf("=");
        String key = null;
        try {
            key = toBeSplit.substring(0, assignmentIndex);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "illegal property string: " + e.toString());
        }
        return key;
    }

    private String getValue(String toBeSplit) {
        int assignmentIndex = toBeSplit.indexOf("=");
        String value = null;
        try {
            value = toBeSplit.substring(assignmentIndex + 1, toBeSplit.length());
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "illegal property string: " + e.toString());
        }
        return value;
     }

    private List<String> readFromFile(File customGlobalFile) {
        int length = (int) customGlobalFile.length();
        byte[] bytes = new byte[length];
        List<String> fileContents = new ArrayList<String>();
        try {
            FileInputStream inputStream = new FileInputStream(customGlobalFile);
            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                while ((receiveString = bufferedReader.readLine()) != null) {
                    fileContents.add(receiveString);
                }
                inputStream.close();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
        }
        return fileContents;
    }

    private void enableApps(String appPackage) {
        Log.d(TAG, "enablingApp :" + appPackage);
        try {
            mPm.setApplicationEnabledSetting(
                    appPackage, mPm.COMPONENT_ENABLED_STATE_ENABLED, mPm.DONT_KILL_APP);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "enabling illegal package: " + e.toString());
        }
    }

    private void disableApps(String appPackage) {
        Log.d(TAG, "disablingApp :" + appPackage);
        try {
            mPm.setApplicationEnabledSetting(
                    appPackage, mPm.COMPONENT_ENABLED_STATE_DISABLED, mPm.DONT_KILL_APP);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "disabling illegal package: " + e.toString());
        }
    }


    /** Handler to handle UI related operations.
     */
    private class MyHandler extends Handler {

        static final int REBOOT_DIALOG = 0;
        static final int FREEZE_FRAME = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REBOOT_DIALOG:
                    showWaitingScreen((Boolean) msg.obj);
                    break;

                case FREEZE_FRAME:
                    if (freezeFrame() < 0) {
                        Log.e(TAG, "FREEZE FRAME FAILED...NOW WHAT TO DO...:(");
                    } else {
                        Log.d(TAG, "showWaitingScreen Freezed");
                    }
                    break;

                default:
                    Log.d(TAG, "Wrong message reason");
                    break;
            }
        }
    }
}