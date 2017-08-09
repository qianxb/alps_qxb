/* drivers/i2c/chips/sc7660.c - SC7660 motion sensor driver
 *
 *
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#include <cust_acc.h>
#include "sc7660.h"
#if MTK_ANDROID_M
#include <accel.h>
#else
#include <linux/interrupt.h>
#include <linux/i2c.h>
#include <linux/slab.h>
#include <linux/irq.h>
#include <linux/miscdevice.h>
#include <linux/uaccess.h>
#include <linux/delay.h>
#include <linux/input.h>
#include <linux/workqueue.h>
#include <linux/kobject.h>
//#include <linux/earlysuspend.h>
#include <linux/platform_device.h>
#include <linux/atomic.h>
#include "upmu_sw.h"
#include "upmu_common.h"
#include "batch.h"
#include <accel.h>
#include <cust_acc.h>
#include <linux/hwmsensor.h>
#include <linux/hwmsen_dev.h>
#include <linux/sensors_io.h>
#include <linux/hwmsen_helper.h>

#include <mach/mt_typedefs.h>
#include <mach/mt_gpio.h>
#include <mach/mt_pm_ldo.h>

#define POWER_NONE_MACRO MT65XX_POWER_NONE
#endif


/*----------------------------------------------------------------------------*/
//#define I2C_DRIVERID_SC7660 345
/*----------------------------------------------------------------------------*/
#define DEBUG 1
/*----------------------------------------------------------------------------*/
//#define CONFIG_SC7660_LOWPASS   /*apply low pass filter on output*/       
#define SW_CALIBRATION
/*----------------------------------------------------------------------------*/
#define SC7660_AXIS_X          0
#define SC7660_AXIS_Y          1
#define SC7660_AXIS_Z          2
#define SC7660_AXES_NUM        3
#define SC7660_DATA_LEN        6
#define SC7660_DEV_NAME        "sc7a30"
#define C_I2C_FIFO_SIZE     8
/*----------------------------------------------------------------------------*/
static const struct i2c_device_id sc7660_i2c_id[] = {{SC7660_DEV_NAME,0},{}};
/*the adapter id will be available in customization*/
//static struct i2c_board_info __initdata i2c_sc7660={ I2C_BOARD_INFO("SC7660", SC7660_I2C_SLAVE_ADDR>>1)};

//static unsigned short sc7660_force[] = {0x00, SC7660_I2C_SLAVE_ADDR, I2C_CLIENT_END, I2C_CLIENT_END};
//static const unsigned short *const sc7660_forces[] = { sc7660_force, NULL };
//static struct i2c_client_address_data sc7660_addr_data = { .forces = sc7660_forces,};

/*----------------------------------------------------------------------------*/
static int sc7660_i2c_probe(struct i2c_client *client, const struct i2c_device_id *id); 
static int sc7660_i2c_remove(struct i2c_client *client);
//static int sc7660_i2c_detect(struct i2c_client *client, int kind, struct i2c_board_info *info);
#ifndef CONFIG_HAS_EARLYSUSPEND
static int sc7660_suspend(struct i2c_client *client, pm_message_t msg);
static int sc7660_resume(struct i2c_client *client);
#endif

extern struct acc_hw *get_cust_sc7660_acc_hw(void);

static int sc7660_local_init(void);
static int  sc7660_remove(void);
/*----------------------------------------------------------------------------*/
typedef enum {
    ADX_TRC_FILTER  = 0x01,
    ADX_TRC_RAWDATA = 0x02,
    ADX_TRC_IOCTL   = 0x04,
    ADX_TRC_CALI	= 0X08,
    ADX_TRC_INFO	= 0X10,
} ADX_TRC;
/*----------------------------------------------------------------------------*/
struct scale_factor{
    int  whole;
    int  fraction;
};
/*----------------------------------------------------------------------------*/
struct data_resolution {
    struct scale_factor scalefactor;
    int                 sensitivity;
};
/*----------------------------------------------------------------------------*/
#define C_MAX_FIR_LENGTH (32)
/*----------------------------------------------------------------------------*/
struct data_filter {
    s16 raw[C_MAX_FIR_LENGTH][SC7660_AXES_NUM];
    int sum[SC7660_AXES_NUM];
    int num;
    int idx;
};


/*----------------------------------------------------------------------------*/
struct sc7660_i2c_data {
    struct i2c_client *client;
    struct acc_hw *hw;
    struct hwmsen_convert   cvt;
    
    /*misc*/
    struct data_resolution *reso;
    atomic_t                trace;
    atomic_t                suspend;
    atomic_t                selftest;
	atomic_t				filter;
    s16                     cali_sw[SC7660_AXES_NUM+1];

    /*data*/
    s8                      offset[SC7660_AXES_NUM+1];  /*+1: for 4-byte alignment*/
    s16                     data[SC7660_AXES_NUM+1];

#if defined(CONFIG_SC7660_LOWPASS)
    atomic_t                firlen;
    atomic_t                fir_en;
    struct data_filter      fir;
#endif 
    /*early suspend*/
#if defined(CONFIG_HAS_EARLYSUSPEND)
    struct early_suspend    early_drv;
#endif     
};

#ifdef CONFIG_OF
static const struct of_device_id accel_of_match[] = {
	{.compatible = "mediatek,gsensor"},
	{},
};
#endif

/* Maintain  cust info here */
static struct acc_hw accel_cust;
static struct acc_hw *hw = &accel_cust;

/* For  driver get cust info */
//struct acc_hw *get_cust_acc(void) {
//    return &accel_cust;
//}

/*----------------------------------------------------------------------------*/
static struct i2c_driver sc7660_i2c_driver = {
    .driver = {
       // .owner          = THIS_MODULE,
        .name           = SC7660_DEV_NAME,
  #ifdef CONFIG_OF
        .of_match_table = accel_of_match,
  #endif
    },
    .probe      	= sc7660_i2c_probe,
    .remove    		= sc7660_i2c_remove,
	//.detect				= sc7660_i2c_detect,
    .suspend            = sc7660_suspend,
    .resume             = sc7660_resume,
    .id_table           = sc7660_i2c_id,
	//.address_data = &sc7660_addr_data,
};

/*----------------------------------------------------------------------------*/
static struct i2c_client *sc7660_i2c_client = NULL;

static struct sc7660_i2c_data *obj_i2c_data = NULL;
static bool sensor_power = false;
static struct GSENSOR_VECTOR3D gsensor_gain;
static char selftestRes[10] = {0};

static DEFINE_MUTEX(sc7660_mutex);
static DEFINE_MUTEX(SC7660_i2c_mutex);
static bool enable_status = false;

static int sc7660_init_flag =-1; // 0<==>OK -1 <==> fail

static struct acc_init_info sc7660_init_info = {
		.name = "sc7a30",
		.init = sc7660_local_init,
		.uninit = sc7660_remove,
	
};



/*----------------------------------------------------------------------------*/
#define GSE_TAG                  "[Gsensor] "
#define GSE_FUN(f)               printk(KERN_ERR GSE_TAG"%s\n", __FUNCTION__)
#define GSE_ERR(format, ...)     printk(KERN_ERR GSE_TAG format "\n", ## __VA_ARGS__);
#define GSE_LOG(format, ...)     printk(KERN_ERR GSE_TAG format "\n", ## __VA_ARGS__);

/*----------------------------------------------------------------------------*/
static struct data_resolution sc7660_data_resolution[] = {
 /*8 combination by {FULL_RES,RANGE}*/
    {{ 0, 975}, 1024},  //refer to datasheet {{ 0, 975}, 1024} /*+/-2g  in 11-bit resolution:  0.975 mg/LSB  1g is devided into 1024 block*/ 
    {{ 3, 9}, 256},   /*+/-8g  in 11-bit resolution:  3.9 mg/LSB 1g is devided into 256 block*/
};
/*----------------------------------------------------------------------------*/
static struct data_resolution sc7660_offset_resolution = {{0, 975}, 1024};
 
#if 0
static int sc7660_i2c_read_block(struct i2c_client *client, u8 addr, u8 *data, u8 len)
{
	u8 beg = addr;
	int err;
	struct i2c_msg msgs[2] = {{0}, {0} };

	mutex_lock(&SC7660_i2c_mutex);

	msgs[0].addr = client->addr;
	msgs[0].flags = 0;
	msgs[0].len = 1;
	msgs[0].buf = &beg;

	msgs[1].addr = client->addr;
	msgs[1].flags = I2C_M_RD;
	msgs[1].len = len;
	msgs[1].buf = data;

	if (!client) {
		mutex_unlock(&SC7660_i2c_mutex);
		return -EINVAL;
	} else if (len > C_I2C_FIFO_SIZE) {
		GSE_ERR(" length %d exceeds %d\n", len, C_I2C_FIFO_SIZE);
		mutex_unlock(&SC7660_i2c_mutex);
		return -EINVAL;
	}
	err = i2c_transfer(client->adapter, msgs, sizeof(msgs)/sizeof(msgs[0]));
	if (err != 2) {
		GSE_ERR("i2c_transfer error: (%d %p %d) %d\n", addr, data, len, err);
		err = -EIO;
	} else
		err = 0;

	mutex_unlock(&SC7660_i2c_mutex);
	return err;

}

static int sc7660_i2c_write_block(struct i2c_client *client, u8 addr, u8 *data, u8 len)
{   /*because address also occupies one byte, the maximum length for write is 7 bytes*/
	int err, idx, num;
	char buf[C_I2C_FIFO_SIZE];

	err = 0;
	mutex_lock(&SC7660_i2c_mutex);
	if (!client) {
		mutex_unlock(&SC7660_i2c_mutex);
		return -EINVAL;
	} else if (len >= C_I2C_FIFO_SIZE) {
		GSE_ERR(" length %d exceeds %d\n", len, C_I2C_FIFO_SIZE);
		mutex_unlock(&SC7660_i2c_mutex);
		return -EINVAL;
	}

	num = 0;
	buf[num++] = addr;
	for (idx = 0; idx < len; idx++)
		buf[num++] = data[idx];

	err = i2c_master_send(client, buf, num);
	if (err < 0) {
		GSE_ERR("send command error!!\n");
		mutex_unlock(&SC7660_i2c_mutex);
		return -EFAULT;
	}
	err = 0;

	mutex_unlock(&SC7660_i2c_mutex);
	return err;
}
#endif

static int hwmsen_read_byte_sr(struct i2c_client *client, u8 addr, u8 *data)
{
    int ret = 0;
    u8 buf[2];
 
 
	mutex_lock(&SC7660_i2c_mutex);
    buf[0] = addr;
    ret = i2c_master_send(client, buf, 0x1);
    if(ret <= 0)
    {
	GSE_ERR("hwmsen_read_byte_sr error: %d\n", ret);
		mutex_unlock(&SC7660_i2c_mutex);
	return -EFAULT;
    }
    ret = i2c_master_recv(client, buf, 0x1);
    if (ret < 0) {
        GSE_ERR("send command error!!\n");
		mutex_unlock(&SC7660_i2c_mutex);
        return -EFAULT;
    }
 
    *data = buf[0];

		mutex_unlock(&SC7660_i2c_mutex);

    return 0;
}

/*--------------------ADXL power control function----------------------------------*/
static void SC7660_power(struct acc_hw *hw, unsigned int on) 
{
#if !MTK_ANDROID_M
dd  
	static unsigned int power_on = 0;

	if(hw->power_id != POWER_NONE_MACRO)		// have externel LDO
	{        
		GSE_LOG("power %s\n", on ? "on" : "off");
		if(power_on == on)	// power status not change
		{
			GSE_LOG("ignore power control: %d\n", on);
		}
		else if(on)	// power on
		{
			if(!hwPowerOn(hw->power_id, hw->power_vol, "SC7660"))
			{
				GSE_ERR("power on fails!!\n");
			}
		}
		else	// power off
		{
			if (!hwPowerDown(hw->power_id, "SC7660"))
			{
				GSE_ERR("power off fail!!\n");
			}			  
		}
	}
	power_on = on;  
#endif  
}
/*----------------------------------------------------------------------------*/
static int SC7660_SetDataResolution(struct sc7660_i2c_data *obj)
{
	int err;
	u8  dat, reso;

        err = hwmsen_read_byte_sr(obj->client, SC7660_REG_CTL_REG4, &dat);
	if(err)
	//	if(err ==sc7660_i2c_read_block(obj->client, SC7660_REG_CTL_REG4, &dat,0x01) )
	{
		GSE_ERR("write data format fail!!\n");
		return err;
	}

	/*the data_reso is combined by 3 bits: {FULL_RES, DATA_RANGE}*/
	reso  = (dat & SC7660_RANGE_8G) ? (0x01) : (0x00);
	
	

	if(reso < sizeof(sc7660_data_resolution)/sizeof(sc7660_data_resolution[0]))
	{        
		obj->reso = &sc7660_data_resolution[reso];
		return 0;
	}
	else
	{
		return -EINVAL;
	}
}
/*----------------------------------------------------------------------------*/
static int SC7660_ReadData(struct i2c_client *client, s16 data[SC7660_AXES_NUM])
{
	struct sc7660_i2c_data *priv = i2c_get_clientdata(client);        
//	u8 addr = SC7660_REG_DATAX0;
	u8 buf[SC7660_DATA_LEN] = {0};
	int err = 0;

	if(NULL == client)
	{
		err = -EINVAL;
	}
	
	else
	{
            
		if(hwmsen_read_byte_sr(client, 0x29,&buf[0]))
	    {
		   GSE_ERR("read X register err!\n");
		     return -1;
	    }

		if(hwmsen_read_byte_sr(client, 0x28,&buf[1]))
	    {
		   GSE_ERR("read X register err!\n");
		     return -1;
	    }
           data[SC7660_AXIS_X] = (((s16) ((buf[0] << 8) | buf[1])) >> 4);	
		
	    if(hwmsen_read_byte_sr(client, 0x2b,&buf[0]))
	    {
		   GSE_ERR("read X register err!\n");
		    return -1;
	     }

		if(hwmsen_read_byte_sr(client, 0x2a,&buf[1]))
	    {
		   GSE_ERR("read X register err!\n");
		    return -1;
	     }
	     data[SC7660_AXIS_Y] =  (((s16) ((buf[0] << 8) | buf[1])) >> 4);	
	 
	    if(hwmsen_read_byte_sr(client, 0x2d, &buf[0]))
	    {
		  GSE_ERR("read X register err!\n");
		   return -1;
	  
	    }

		if(hwmsen_read_byte_sr(client, 0x2c, &buf[1]))
	    {
		  GSE_ERR("read X register err!\n");
		   return -1;
	  
	    }
	    data[SC7660_AXIS_Z] =  (((s16) ((buf[0] << 8) | buf[1])) >> 4);	

           // printk("sc7660x= %d\n sc7660y= %d\n sc7660z= %d\n",data[SC7660_AXIS_X],data[SC7660_AXIS_Y],data[SC7660_AXIS_Z]);
/////////////////////////////////////////////////////////////
		if(atomic_read(&priv->trace) & ADX_TRC_RAWDATA)
		{
			GSE_LOG("[%08X %08X %08X] => [%5d %5d %5d]\n", data[SC7660_AXIS_X], data[SC7660_AXIS_Y], data[SC7660_AXIS_Z],
		                               data[SC7660_AXIS_X], data[SC7660_AXIS_Y], data[SC7660_AXIS_Z]);
		}
#ifdef CONFIG_SC7660_LOWPASS
		if(atomic_read(&priv->filter))
		{
			if(atomic_read(&priv->fir_en) && !atomic_read(&priv->suspend))
			{
				int idx, firlen = atomic_read(&priv->firlen);   
				if(priv->fir.num < firlen)
				{                
					priv->fir.raw[priv->fir.num][SC7660_AXIS_X] = data[SC7660_AXIS_X];
					priv->fir.raw[priv->fir.num][SC7660_AXIS_Y] = data[SC7660_AXIS_Y];
					priv->fir.raw[priv->fir.num][SC7660_AXIS_Z] = data[SC7660_AXIS_Z];
					priv->fir.sum[SC7660_AXIS_X] += data[SC7660_AXIS_X];
					priv->fir.sum[SC7660_AXIS_Y] += data[SC7660_AXIS_Y];
					priv->fir.sum[SC7660_AXIS_Z] += data[SC7660_AXIS_Z];
					if(atomic_read(&priv->trace) & ADX_TRC_FILTER)
					{
						GSE_LOG("add [%2d] [%5d %5d %5d] => [%5d %5d %5d]\n", priv->fir.num,
							priv->fir.raw[priv->fir.num][SC7660_AXIS_X], priv->fir.raw[priv->fir.num][SC7660_AXIS_Y], priv->fir.raw[priv->fir.num][SC7660_AXIS_Z],
							priv->fir.sum[SC7660_AXIS_X], priv->fir.sum[SC7660_AXIS_Y], priv->fir.sum[SC7660_AXIS_Z]);
					}
					priv->fir.num++;
					priv->fir.idx++;
				}
				else
				{
					idx = priv->fir.idx % firlen;
					priv->fir.sum[SC7660_AXIS_X] -= priv->fir.raw[idx][SC7660_AXIS_X];
					priv->fir.sum[SC7660_AXIS_Y] -= priv->fir.raw[idx][SC7660_AXIS_Y];
					priv->fir.sum[SC7660_AXIS_Z] -= priv->fir.raw[idx][SC7660_AXIS_Z];
					priv->fir.raw[idx][SC7660_AXIS_X] = data[SC7660_AXIS_X];
					priv->fir.raw[idx][SC7660_AXIS_Y] = data[SC7660_AXIS_Y];
					priv->fir.raw[idx][SC7660_AXIS_Z] = data[SC7660_AXIS_Z];
					priv->fir.sum[SC7660_AXIS_X] += data[SC7660_AXIS_X];
					priv->fir.sum[SC7660_AXIS_Y] += data[SC7660_AXIS_Y];
					priv->fir.sum[SC7660_AXIS_Z] += data[SC7660_AXIS_Z];
					priv->fir.idx++;
					data[SC7660_AXIS_X] = priv->fir.sum[SC7660_AXIS_X]/firlen;
					data[SC7660_AXIS_Y] = priv->fir.sum[SC7660_AXIS_Y]/firlen;
					data[SC7660_AXIS_Z] = priv->fir.sum[SC7660_AXIS_Z]/firlen;
					if(atomic_read(&priv->trace) & ADX_TRC_FILTER)
					{
						GSE_LOG("add [%2d] [%5d %5d %5d] => [%5d %5d %5d] : [%5d %5d %5d]\n", idx,
						priv->fir.raw[idx][SC7660_AXIS_X], priv->fir.raw[idx][SC7660_AXIS_Y], priv->fir.raw[idx][SC7660_AXIS_Z],
						priv->fir.sum[SC7660_AXIS_X], priv->fir.sum[SC7660_AXIS_Y], priv->fir.sum[SC7660_AXIS_Z],
						data[SC7660_AXIS_X], data[SC7660_AXIS_Y], data[SC7660_AXIS_Z]);
					}
				}
			}
		}	
#endif         
	}
	return err;
}
/*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*/
static int SC7660_ResetCalibration(struct i2c_client *client)
{
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);	

	memset(obj->cali_sw, 0x00, sizeof(obj->cali_sw));
	return 0;     
}
/*----------------------------------------------------------------------------*/
static int SC7660_ReadCalibration(struct i2c_client *client, int dat[SC7660_AXES_NUM])
{
    struct sc7660_i2c_data *obj = i2c_get_clientdata(client);

    dat[obj->cvt.map[SC7660_AXIS_X]] = obj->cvt.sign[SC7660_AXIS_X]*obj->cali_sw[SC7660_AXIS_X];
    dat[obj->cvt.map[SC7660_AXIS_Y]] = obj->cvt.sign[SC7660_AXIS_Y]*obj->cali_sw[SC7660_AXIS_Y];
    dat[obj->cvt.map[SC7660_AXIS_Z]] = obj->cvt.sign[SC7660_AXIS_Z]*obj->cali_sw[SC7660_AXIS_Z];                        
                                       
    return 0;
}
/*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*/
static int SC7660_WriteCalibration(struct i2c_client *client, int dat[SC7660_AXES_NUM])
{
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);
	int err = 0;
//	int cali[SC7660_AXES_NUM];


	GSE_FUN();
	if(!obj || ! dat)
	{
		GSE_ERR("null ptr!!\n");
		return -EINVAL;
	}
	else
	{        
		s16 cali[SC7660_AXES_NUM];
		cali[obj->cvt.map[SC7660_AXIS_X]] = obj->cvt.sign[SC7660_AXIS_X]*obj->cali_sw[SC7660_AXIS_X];
		cali[obj->cvt.map[SC7660_AXIS_Y]] = obj->cvt.sign[SC7660_AXIS_Y]*obj->cali_sw[SC7660_AXIS_Y];
		cali[obj->cvt.map[SC7660_AXIS_Z]] = obj->cvt.sign[SC7660_AXIS_Z]*obj->cali_sw[SC7660_AXIS_Z]; 
		cali[SC7660_AXIS_X] += dat[SC7660_AXIS_X];
		cali[SC7660_AXIS_Y] += dat[SC7660_AXIS_Y];
		cali[SC7660_AXIS_Z] += dat[SC7660_AXIS_Z];

		obj->cali_sw[SC7660_AXIS_X] += obj->cvt.sign[SC7660_AXIS_X]*dat[obj->cvt.map[SC7660_AXIS_X]];
        obj->cali_sw[SC7660_AXIS_Y] += obj->cvt.sign[SC7660_AXIS_Y]*dat[obj->cvt.map[SC7660_AXIS_Y]];
        obj->cali_sw[SC7660_AXIS_Z] += obj->cvt.sign[SC7660_AXIS_Z]*dat[obj->cvt.map[SC7660_AXIS_Z]];
	} 

	return err;
}
/*----------------------------------------------------------------------------*/
static int SC7660_CheckDeviceID(struct i2c_client *client)
{
	u8 databuf[2];    
	int res = 0;

        GSE_FUN();
	memset(databuf, 0, sizeof(u8)*2);    
	databuf[0] = SC7660_REG_DEVID;    

        res = hwmsen_read_byte_sr(client,SC7660_REG_DEVID,databuf);
	if(res < 0)
	{
		printk("SC7660_CheckDeviceID read err\n ");
		goto exit_SC7660_CheckDeviceID;
	}
	if(databuf[0]!=SC7660_FIXED_DEVID)
	{
		printk("SC7660_CheckDeviceID %d failt!\n ", databuf[0]);
		return SC7660_ERR_IDENTIFICATION;
	}
	else
	{
		printk("SC7660_CheckDeviceID %d pass!\n ", databuf[0]);
	}

exit_SC7660_CheckDeviceID:
	if (res < 0)
	{
		return SC7660_ERR_I2C;
	}
	
	return SC7660_SUCCESS;
}
/*----------------------------------------------------------------------------*/
static int SC7660_SetPowerMode(struct i2c_client *client, bool enable)
{
	u8 databuf[2];    
	int res = 0;
	u8 addr = SC7660_REG_CTL_REG1;
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);
	
	GSE_FUN();

	if(enable == sensor_power)
	{
		GSE_LOG("Sensor power status is newest!\n");
		return SC7660_SUCCESS;
	}

	if(hwmsen_read_byte_sr(client, addr, &databuf[0]))
	{
		GSE_ERR("read power ctl register err!\n");
		return SC7660_ERR_I2C;
	}

	databuf[0] &= ~SC7660_MEASURE_MODE;
	
	if(enable == true)
	{
		databuf[0] |= SC7660_ACTIVE_MODE;//SC7660_MEASURE_MODE;
	}
	else
	{
		// do nothing
	}
	databuf[1] = databuf[0];
	databuf[0] = SC7660_REG_CTL_REG1;
	

	res = i2c_master_send(client, databuf, 0x2);

	if(res <= 0)
	{
		GSE_LOG("set power mode failed!\n");
		return SC7660_ERR_I2C;
	}
	else if(atomic_read(&obj->trace) & ADX_TRC_INFO)
	{
		GSE_LOG("set power mode ok %d!\n", databuf[1]);
	}

	sensor_power = enable;
	
	return SC7660_SUCCESS;    
}
/*----------------------------------------------------------------------------*/
static int SC7660_SetDataFormat(struct i2c_client *client, u8 dataformat)
{
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);
	u8 databuf[10];
	u8 addr = SC7660_REG_CTL_REG4;
	int res = 0;

	memset(databuf, 0, sizeof(u8)*10);

	if(hwmsen_read_byte_sr(client, addr, &databuf[0]))
	{
		GSE_ERR("read reg_ctl_reg1 register err!\n");
		return SC7660_ERR_I2C;
	}

	databuf[0] &= ~SC7660_RANGE;
	
	if(SC7660_RANGE_8G == dataformat)
	{
		databuf[0] |= SC7660_RANGE_8G;
	}
	else
	{
	    GSE_LOG("set 2g range\n");
		databuf[0] |= SC7660_RANGE_2G;
	}
	databuf[0] |= 0x88;//allen
	databuf[1] = databuf[0];
	databuf[0] = SC7660_REG_CTL_REG4;
	

	res = i2c_master_send(client, databuf, 0x2);

	if(res <= 0)
	{
		return SC7660_ERR_I2C;
	}
	

	return SC7660_SetDataResolution(obj);    
}
/*----------------------------------------------------------------------------*/
static int SC7660_SetBWRate(struct i2c_client *client, u8 bwrate)
{
	u8 databuf[10];
	u8 addr = SC7660_REG_CTL_REG1;
	int res = 0;

	memset(databuf, 0, sizeof(u8)*10);
	
	if(hwmsen_read_byte_sr(client, addr, &databuf[0]))
	{
		GSE_ERR("read reg_ctl_reg1 register err!\n");
		return SC7660_ERR_I2C;
	}

	databuf[0] &= ~SC7660_BW;
	
	if(SC7660_BW_400HZ == bwrate)
	{
		databuf[0] |= SC7660_BW_400HZ;
	}
	else
	{
	     GSE_LOG("set DataRate 100Hz\n");
		 databuf[0] |= SC7660_BW_100HZ;
	}
	databuf[1] = databuf[0];
	databuf[0] = SC7660_REG_CTL_REG1;

	res = i2c_master_send(client, databuf, 0x2);

	if(res <= 0)
	{
		return SC7660_ERR_I2C;
	}
	
	return SC7660_SUCCESS;    
}
/*----------------------------------------------------------------------------*/
//enalbe data ready interrupt
static int SC7660_SetIntEnable(struct i2c_client *client, u8 intenable)
{
	u8 databuf[10];
	u8 addr = SC7660_REG_CTL_REG1;
	int res = 0;

	memset(databuf, 0, sizeof(u8)*10); 

	if(hwmsen_read_byte_sr(client, addr, &databuf[0]))
	{
		GSE_ERR("read reg_ctl_reg1 register err!\n");
		return SC7660_ERR_I2C;
	}

	databuf[0] &= ~SC7660_DATA_READY;
	
	if(SC7660_DATA_READY == intenable)
	{
		databuf[0] |= SC7660_DATA_READY;
	}
	else
	{
	     GSE_LOG("Disable dataready INT\n");
		// do nothing
	}
	databuf[1] = databuf[0];
	databuf[0] = SC7660_REG_CTL_REG1;
	
	res = i2c_master_send(client, databuf, 0x2);

	if(res <= 0)
	{
		return SC7660_ERR_I2C;
	}
	
	return SC7660_SUCCESS;    
}
/*----------------------------------------------------------------------------*/
static int SC7660_Init(struct i2c_client *client, int reset_cali)
{
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);
	int res = 0;

        GSE_FUN();

	res = SC7660_CheckDeviceID(client); 
	if(res != SC7660_SUCCESS)
	{
		return res;
	}	

    // first clear reg1
    res = hwmsen_write_byte(client,SC7660_REG_CTL_REG1,0x00);
	if(res != SC7660_SUCCESS)
	{
		return res;
	}


	res = SC7660_SetPowerMode(client, false);
	if(res != SC7660_SUCCESS)
	{
		return res;
	}
	

	res = SC7660_SetBWRate(client, SC7660_BW_100HZ);//400 or 100 no other choice
	if(res != SC7660_SUCCESS )
	{
		return res;
	}

	res = SC7660_SetDataFormat(client, SC7660_RANGE_2G);//8g or 2G no oher choise
	if(res != SC7660_SUCCESS) 
	{
		return res;
	}
	gsensor_gain.x = gsensor_gain.y = gsensor_gain.z = obj->reso->sensitivity;

	res = SC7660_SetIntEnable(client, SC7660_DATA_READY);        
	if(res != SC7660_SUCCESS)
	{
		return res;
	}
	

	if(0 != reset_cali)
	{ 
		//reset calibration only in power on
		res = SC7660_ResetCalibration(client);
		if(res != SC7660_SUCCESS)
		{
			return res;
		}
	}

#ifdef CONFIG_SC7660_LOWPASS
	memset(&obj->fir, 0x00, sizeof(obj->fir));  
#endif

	return SC7660_SUCCESS;
}
/*----------------------------------------------------------------------------*/
static int SC7660_ReadChipInfo(struct i2c_client *client, char *buf, int bufsize)
{
	u8 databuf[10];    

	memset(databuf, 0, sizeof(u8)*10);

	if((NULL == buf)||(bufsize<=30))
	{
		return -1;
	}
	
	if(NULL == client)
	{
		*buf = 0;
		return -2;
	}

	sprintf(buf, "SC7660 Chip");
	return 0;
}
/*----------------------------------------------------------------------------*/
static int SC7660_ReadSensorData(struct i2c_client *client, char *buf, int bufsize)
{
	struct sc7660_i2c_data *obj = (struct sc7660_i2c_data*)i2c_get_clientdata(client);
	u8 databuf[20];
	int acc[SC7660_AXES_NUM];
	int res = 0;
	memset(databuf, 0, sizeof(u8)*10);

	if(NULL == buf)
	{
		return -1;
	}
	if(NULL == client)
	{
		*buf = 0;
		return -2;
	}

	if(sensor_power == false)
	{
		res = SC7660_SetPowerMode(client, true);
		if(res)
		{
			GSE_ERR("Power on sc7660 error %d!\n", res);
		}
		msleep(20);
	}

        res = SC7660_ReadData(client, obj->data);
	if(res)
	{        
		GSE_ERR("I2C error: ret value=%d", res);
		return -3;
	}
	else
	{
		obj->data[SC7660_AXIS_X] += obj->cali_sw[SC7660_AXIS_X];
		obj->data[SC7660_AXIS_Y] += obj->cali_sw[SC7660_AXIS_Y];
		obj->data[SC7660_AXIS_Z] += obj->cali_sw[SC7660_AXIS_Z];
		
		/*remap coordinate*/
		acc[obj->cvt.map[SC7660_AXIS_X]] = obj->cvt.sign[SC7660_AXIS_X]*obj->data[SC7660_AXIS_X];
		acc[obj->cvt.map[SC7660_AXIS_Y]] = obj->cvt.sign[SC7660_AXIS_Y]*obj->data[SC7660_AXIS_Y];
		acc[obj->cvt.map[SC7660_AXIS_Z]] = obj->cvt.sign[SC7660_AXIS_Z]*obj->data[SC7660_AXIS_Z];

		//GSE_LOG("Mapped gsensor data: %d, %d, %d!\n", acc[SC7660_AXIS_X], acc[SC7660_AXIS_Y], acc[SC7660_AXIS_Z]);

		//Out put the mg
		acc[SC7660_AXIS_X] = acc[SC7660_AXIS_X] * GRAVITY_EARTH_1000 / obj->reso->sensitivity;
		acc[SC7660_AXIS_Y] = acc[SC7660_AXIS_Y] * GRAVITY_EARTH_1000 / obj->reso->sensitivity;
		acc[SC7660_AXIS_Z] = acc[SC7660_AXIS_Z] * GRAVITY_EARTH_1000 / obj->reso->sensitivity;		
		

		sprintf(buf, "%04x %04x %04x", acc[SC7660_AXIS_X], acc[SC7660_AXIS_Y], acc[SC7660_AXIS_Z]);
		if(atomic_read(&obj->trace) & ADX_TRC_IOCTL)//atomic_read(&obj->trace) & ADX_TRC_IOCTL
		{
			GSE_LOG("gsensor data: %s!\n", buf);
			
		}
	}
	
	return 0;
}
/*----------------------------------------------------------------------------*/
static int SC7660_ReadRawData(struct i2c_client *client, char *buf)
{
	struct sc7660_i2c_data *obj = (struct sc7660_i2c_data*)i2c_get_clientdata(client);
	int res = 0;

	if (!buf || !client)
	{
		return EINVAL;
	}
	
        res = SC7660_ReadData(client, obj->data);
	if(res)
	{        
		GSE_ERR("I2C error: ret value=%d", res);
		return EIO;
	}
	else
	{
		sprintf(buf, "%04x %04x %04x", obj->data[SC7660_AXIS_X], 
			obj->data[SC7660_AXIS_Y], obj->data[SC7660_AXIS_Z]);
	
	}
	
	return 0;
}
/*----------------------------------------------------------------------------*/
static int SC7660_InitSelfTest(struct i2c_client *client)
{
	int res = 0;
	u8  data;

	res = SC7660_SetBWRate(client, SC7660_BW_400HZ);
	if(res != SC7660_SUCCESS ) 
	{
		return res;
	}
	
	res = hwmsen_read_byte_sr(client, SC7660_REG_CTL_REG4, &data);
	if(res != SC7660_SUCCESS)
	{
		return res;
	}
	

	res = SC7660_SetDataFormat(client, SC7660_SELF_TEST|data);
	if(res != SC7660_SUCCESS) 
	{
		return res;
	}
	
	res = SC7660_SetPowerMode(client,true);
	if(res != SC7660_SUCCESS) 
	{
		return res;
	}
	
	return SC7660_SUCCESS;
}
/*----------------------------------------------------------------------------*/
static int SC7660_JudgeTestResult(struct i2c_client *client, s32 prv[SC7660_AXES_NUM], s32 nxt[SC7660_AXES_NUM])
{
    struct criteria {
        int min;
        int max;
    };
	
    struct criteria self[4][3] = {
        {{-35, 5}, {-35, 5}, {-40, -3}},//test range 8g
        {{-35, 5}, {-35, 5}, {-40, -3}},//test range 2g
                   
    };
    struct criteria (*ptr)[3] = NULL;
    u8 format;
    int res;

    res = hwmsen_read_byte_sr(client, SC7660_REG_CTL_REG4, &format);
    if(res)
        return res;
    if(format & SC7660_RANGE_8G)
        ptr = &self[0];
    else 
        ptr = &self[1];
    

    if (!ptr) {
        GSE_ERR("null pointer\n");
        return -EINVAL;
    }

    if (((nxt[SC7660_AXIS_X] - prv[SC7660_AXIS_X]) > (*ptr)[SC7660_AXIS_X].max) ||
        ((nxt[SC7660_AXIS_X] - prv[SC7660_AXIS_X]) < (*ptr)[SC7660_AXIS_X].min)) {
        GSE_ERR("X is over range\n");
        res = -EINVAL;
    }
    if (((nxt[SC7660_AXIS_Y] - prv[SC7660_AXIS_Y]) > (*ptr)[SC7660_AXIS_Y].max) ||
        ((nxt[SC7660_AXIS_Y] - prv[SC7660_AXIS_Y]) < (*ptr)[SC7660_AXIS_Y].min)) {
        GSE_ERR("Y is over range\n");
        res = -EINVAL;
    }
    if (((nxt[SC7660_AXIS_Z] - prv[SC7660_AXIS_Z]) > (*ptr)[SC7660_AXIS_Z].max) ||
        ((nxt[SC7660_AXIS_Z] - prv[SC7660_AXIS_Z]) < (*ptr)[SC7660_AXIS_Z].min)) {
        GSE_ERR("Z is over range\n");
        res = -EINVAL;
    }
    return res;
}
/*----------------------------------------------------------------------------*/
static ssize_t show_chipinfo_value(struct device_driver *ddri, char *buf)
{
	struct i2c_client *client = sc7660_i2c_client;
	char strbuf[SC7660_BUFSIZE];

	if(NULL == client)
	{
		GSE_ERR("i2c client is null!!\n");
		return 0;
	}
	
	SC7660_ReadChipInfo(client, strbuf, SC7660_BUFSIZE);
	return snprintf(buf, PAGE_SIZE, "%s\n", strbuf);        
}
/*----------------------------------------------------------------------------*/
static ssize_t show_sensordata_value(struct device_driver *ddri, char *buf)
{
	struct i2c_client *client = sc7660_i2c_client;
	char strbuf[SC7660_BUFSIZE];
	
	if(NULL == client)
	{
		GSE_ERR("i2c client is null!!\n");
		return 0;
	}
	SC7660_ReadSensorData(client, strbuf, SC7660_BUFSIZE);
	return snprintf(buf, PAGE_SIZE, "%s\n", strbuf);            
}
/*----------------------------------------------------------------------------*/
static ssize_t show_cali_value(struct device_driver *ddri, char *buf)
{
	struct i2c_client *client = sc7660_i2c_client;
	struct sc7660_i2c_data *obj;

	int err, len = 0, mul;
	int tmp[SC7660_AXES_NUM];

	if(NULL == client)
	{
		GSE_ERR("i2c client is null!!\n");
		return 0;
	}

	obj = i2c_get_clientdata(client);

	err = SC7660_ReadCalibration(client, tmp);
	if(err)
	{
		return -EINVAL;
	}
	else
	{    
		mul = obj->reso->sensitivity/sc7660_offset_resolution.sensitivity;
		len += snprintf(buf+len, PAGE_SIZE-len, "[HW ][%d] (%+3d, %+3d, %+3d) : (0x%02X, 0x%02X, 0x%02X)\n", mul,                        
			obj->offset[SC7660_AXIS_X], obj->offset[SC7660_AXIS_Y], obj->offset[SC7660_AXIS_Z],
			obj->offset[SC7660_AXIS_X], obj->offset[SC7660_AXIS_Y], obj->offset[SC7660_AXIS_Z]);
		len += snprintf(buf+len, PAGE_SIZE-len, "[SW ][%d] (%+3d, %+3d, %+3d)\n", 1, 
			obj->cali_sw[SC7660_AXIS_X], obj->cali_sw[SC7660_AXIS_Y], obj->cali_sw[SC7660_AXIS_Z]);

		len += snprintf(buf+len, PAGE_SIZE-len, "[ALL]    (%+3d, %+3d, %+3d) : (%+3d, %+3d, %+3d)\n", 
			obj->offset[SC7660_AXIS_X]*mul + obj->cali_sw[SC7660_AXIS_X],
			obj->offset[SC7660_AXIS_Y]*mul + obj->cali_sw[SC7660_AXIS_Y],
			obj->offset[SC7660_AXIS_Z]*mul + obj->cali_sw[SC7660_AXIS_Z],
			tmp[SC7660_AXIS_X], tmp[SC7660_AXIS_Y], tmp[SC7660_AXIS_Z]);
		
		return len;
    }
}
/*----------------------------------------------------------------------------*/
static ssize_t store_cali_value(struct device_driver *ddri, const char *buf, size_t count)
{
	struct i2c_client *client = sc7660_i2c_client;  
	int err, x, y, z;
	int dat[SC7660_AXES_NUM];

	if(!strncmp(buf, "rst", 3))
	{
                err = SC7660_ResetCalibration(client);
		if(err)
		{
			GSE_ERR("reset offset err = %d\n", err);
		}	
	}
	else if(3 == sscanf(buf, "0x%02X 0x%02X 0x%02X", &x, &y, &z))
	{
		dat[SC7660_AXIS_X] = x;
		dat[SC7660_AXIS_Y] = y;
		dat[SC7660_AXIS_Z] = z;
                err = SC7660_WriteCalibration(client, dat);
		if(err)
		{
			GSE_ERR("write calibration err = %d\n", err);
		}		
	}
	else
	{
		GSE_ERR("invalid format\n");
	}
	
	return count;
}
/*----------------------------------------------------------------------------*/

static ssize_t show_power_status(struct device_driver *ddri, char *buf)
{
	struct i2c_client *client = sc7660_i2c_client;
	struct sc7660_i2c_data *obj;
	u8 data;

	if(NULL == client)
	{
		GSE_ERR("i2c client is null!!\n");
		return 0;
	}

	obj = i2c_get_clientdata(client);
	hwmsen_read_byte_sr(client,SC7660_REG_CTL_REG1,&data);

    return snprintf(buf, PAGE_SIZE, "%x\n", data);
}

/*----------------------------------------------------------------------------*/

/*----------------------------------------------------------------------------*/
static ssize_t show_selftest_value(struct device_driver *ddri, char *buf)
{
	struct i2c_client *client = sc7660_i2c_client;
//	struct sc7660_i2c_data *obj;
//	int result =0;
	if(NULL == client)
	{
		GSE_ERR("i2c client is null!!\n");
		return 0;
	}
	GSE_LOG("fwq  selftestRes value =%s\n",selftestRes); 
	return snprintf(buf, 10, "%s\n", selftestRes);
}

/*----------------------------------------------------------------------------*/
static ssize_t store_selftest_value(struct device_driver *ddri, const char *buf, size_t count)
{
	struct item{
	s16 raw[SC7660_AXES_NUM];
	};
	
	struct i2c_client *client = sc7660_i2c_client;  
	int idx, res, num;
	struct item *prv = NULL, *nxt = NULL;
	s32 avg_prv[SC7660_AXES_NUM] = {0, 0, 0};
	s32 avg_nxt[SC7660_AXES_NUM] = {0, 0, 0};


	if(1 != sscanf(buf, "%d", &num))
	{
		GSE_ERR("parse number fail\n");
		return count;
	}
	else if(num == 0)
	{
		GSE_ERR("invalid data count\n");
		return count;
	}

	prv = kzalloc(sizeof(*prv) * num, GFP_KERNEL);
	nxt = kzalloc(sizeof(*nxt) * num, GFP_KERNEL);
	if (!prv || !nxt)
	{
		goto exit;
	}


    res = SC7660_SetPowerMode(client,true);
	if(res != SC7660_SUCCESS ) //
	{
		return res;
	}
	msleep(20);
	
	GSE_LOG("NORMAL:\n");
	for(idx = 0; idx < num; idx++)
	{
                res = SC7660_ReadData(client, prv[idx].raw);
		if(res)
		{            
			GSE_ERR("read data fail: %d\n", res);
			goto exit;
		}
		
		avg_prv[SC7660_AXIS_X] += prv[idx].raw[SC7660_AXIS_X];
		avg_prv[SC7660_AXIS_Y] += prv[idx].raw[SC7660_AXIS_Y];
		avg_prv[SC7660_AXIS_Z] += prv[idx].raw[SC7660_AXIS_Z];        
		GSE_LOG("[%5d %5d %5d]\n", prv[idx].raw[SC7660_AXIS_X], prv[idx].raw[SC7660_AXIS_Y], prv[idx].raw[SC7660_AXIS_Z]);
	}
	
	avg_prv[SC7660_AXIS_X] /= num;
	avg_prv[SC7660_AXIS_Y] /= num;
	avg_prv[SC7660_AXIS_Z] /= num;  

	res = SC7660_SetPowerMode(client,false);
	if(res != SC7660_SUCCESS ) //
	{
		return res;
	}

	/*initial setting for self test*/
	SC7660_InitSelfTest(client);
	msleep(50);
	GSE_LOG("SELFTEST:\n");    
	for(idx = 0; idx < num; idx++)
	{
                res = SC7660_ReadData(client, nxt[idx].raw);
		if(res)
		{            
			GSE_ERR("read data fail: %d\n", res);
			goto exit;
		}
		avg_nxt[SC7660_AXIS_X] += nxt[idx].raw[SC7660_AXIS_X];
		avg_nxt[SC7660_AXIS_Y] += nxt[idx].raw[SC7660_AXIS_Y];
		avg_nxt[SC7660_AXIS_Z] += nxt[idx].raw[SC7660_AXIS_Z];        
		GSE_LOG("[%5d %5d %5d]\n", nxt[idx].raw[SC7660_AXIS_X], nxt[idx].raw[SC7660_AXIS_Y], nxt[idx].raw[SC7660_AXIS_Z]);
	}
	
	avg_nxt[SC7660_AXIS_X] /= num;
	avg_nxt[SC7660_AXIS_Y] /= num;
	avg_nxt[SC7660_AXIS_Z] /= num;    

	GSE_LOG("X: %5d - %5d = %5d \n", avg_nxt[SC7660_AXIS_X], avg_prv[SC7660_AXIS_X], avg_nxt[SC7660_AXIS_X] - avg_prv[SC7660_AXIS_X]);
	GSE_LOG("Y: %5d - %5d = %5d \n", avg_nxt[SC7660_AXIS_Y], avg_prv[SC7660_AXIS_Y], avg_nxt[SC7660_AXIS_Y] - avg_prv[SC7660_AXIS_Y]);
	GSE_LOG("Z: %5d - %5d = %5d \n", avg_nxt[SC7660_AXIS_Z], avg_prv[SC7660_AXIS_Z], avg_nxt[SC7660_AXIS_Z] - avg_prv[SC7660_AXIS_Z]); 

	if(!SC7660_JudgeTestResult(client, avg_prv, avg_nxt))
	{
		GSE_LOG("SELFTEST : PASS\n");
		strcpy(selftestRes,"y");
	}	
	else
	{
		GSE_LOG("SELFTEST : FAIL\n");
		strcpy(selftestRes,"n");
	}
	
	exit:
	/*restore the setting*/    
	SC7660_Init(client, 0);
	kfree(prv);
	kfree(nxt);
	return count;
}
/*----------------------------------------------------------------------------*/
static ssize_t show_firlen_value(struct device_driver *ddri, char *buf)
{
#ifdef CONFIG_SC7660_LOWPASS
	struct i2c_client *client = sc7660_i2c_client;
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);
	if(atomic_read(&obj->firlen))
	{
		int idx, len = atomic_read(&obj->firlen);
		GSE_LOG("len = %2d, idx = %2d\n", obj->fir.num, obj->fir.idx);

		for(idx = 0; idx < len; idx++)
		{
			GSE_LOG("[%5d %5d %5d]\n", obj->fir.raw[idx][SC7660_AXIS_X], obj->fir.raw[idx][SC7660_AXIS_Y], obj->fir.raw[idx][SC7660_AXIS_Z]);
		}
		
		GSE_LOG("sum = [%5d %5d %5d]\n", obj->fir.sum[SC7660_AXIS_X], obj->fir.sum[SC7660_AXIS_Y], obj->fir.sum[SC7660_AXIS_Z]);
		GSE_LOG("avg = [%5d %5d %5d]\n", obj->fir.sum[SC7660_AXIS_X]/len, obj->fir.sum[SC7660_AXIS_Y]/len, obj->fir.sum[SC7660_AXIS_Z]/len);
	}
	return snprintf(buf, PAGE_SIZE, "%d\n", atomic_read(&obj->firlen));
#else
	return snprintf(buf, PAGE_SIZE, "not support\n");
#endif
}
/*----------------------------------------------------------------------------*/
static ssize_t store_firlen_value(struct device_driver *ddri, const char *buf, size_t count)
{
#ifdef CONFIG_SC7660_LOWPASS
	struct i2c_client *client = sc7660_i2c_client;  
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);
	int firlen;

	if(1 != sscanf(buf, "%d", &firlen))
	{
		GSE_ERR("invallid format\n");
	}
	else if(firlen > C_MAX_FIR_LENGTH)
	{
		GSE_ERR("exceeds maximum filter length\n");
	}
	else
	{ 
		atomic_set(&obj->firlen, firlen);
		if(NULL == firlen)
		{
			atomic_set(&obj->fir_en, 0);
		}
		else
		{
			memset(&obj->fir, 0x00, sizeof(obj->fir));
			atomic_set(&obj->fir_en, 1);
		}
	}
#endif    
	return count;
}
/*----------------------------------------------------------------------------*/
static ssize_t show_trace_value(struct device_driver *ddri, char *buf)
{
	ssize_t res;
	struct sc7660_i2c_data *obj = obj_i2c_data;
	if (obj == NULL)
	{
		GSE_ERR("i2c_data obj is null!!\n");
		return 0;
	}
	
	res = snprintf(buf, PAGE_SIZE, "0x%04X\n", atomic_read(&obj->trace));     
	return res;    
}
/*----------------------------------------------------------------------------*/
static ssize_t store_trace_value(struct device_driver *ddri, const char *buf, size_t count)
{
	struct sc7660_i2c_data *obj = obj_i2c_data;
	int trace;
	if (obj == NULL)
	{
		GSE_ERR("i2c_data obj is null!!\n");
		return 0;
	}
	
	if(1 == sscanf(buf, "0x%x", &trace))
	{
		atomic_set(&obj->trace, trace);
	}	
	else
	{
		GSE_ERR("invalid content: '%s', length = %d\n", buf, count);
	}
	
	return count;    
}
/*----------------------------------------------------------------------------*/
static ssize_t show_status_value(struct device_driver *ddri, char *buf)
{
	ssize_t len = 0;    
	struct sc7660_i2c_data *obj = obj_i2c_data;
	if (obj == NULL)
	{
		GSE_ERR("i2c_data obj is null!!\n");
		return 0;
	}	
	
	if(obj->hw)
	{
		len += snprintf(buf+len, PAGE_SIZE-len, "CUST: %d %d (%d %d)\n", 
	            obj->hw->i2c_num, obj->hw->direction, obj->hw->power_id, obj->hw->power_vol);   
	}
	else
	{
		len += snprintf(buf+len, PAGE_SIZE-len, "CUST: NULL\n");
	}
	return len;    
}


/*----------------------------------------------------------------------------*/
static ssize_t show_reg_value(struct device_driver *ddri, char *buf)
{
    int count = 0;
	//u8 reg[0x5b];
	char i;	
	char buffer;
	i = 0x0f;
    //*buffer = i;
    count = hwmsen_read_byte_sr(sc7660_i2c_client, 0x11, &buffer);
    count += sprintf(buf, "0x%x: 0x%x\n", i, buffer);
    for(i=0x10;i<0x5a;i++)
    {
		//*buffer = i;
	
    	//sensor_rx_data(sc7a30_client, i, 1); 
		hwmsen_read_byte_sr(sc7660_i2c_client,i, &buffer);
		count += sprintf(&buf[count],"0x%x: 0x%x\n", i, buffer);
    }   
    return count;   
}
/*----------------------------------------------------------------------------*/
static ssize_t store_reg_value(struct device_driver *ddri, const char *buf, size_t count)
{
    int address, value;
    int result = 0;
	char databuf[2]={0};
    sscanf(buf, "0x%x=0x%x", &address, &value);    
	
	databuf[1] = value;
	databuf[0] = address;
	

	result = i2c_master_send(sc7660_i2c_client, databuf, 0x2);
    
   // result = sensor_write_reg(sc7a30_client, address,value);

    if(result)
		printk("%s:fail to write sensor_register\n",__func__);

    return count;    
}

/*----------------------------------------------------------------------------*/
static DRIVER_ATTR(chipinfo,             S_IRUGO, show_chipinfo_value,      NULL);
static DRIVER_ATTR(sensordata,           S_IRUGO, show_sensordata_value,    NULL);
static DRIVER_ATTR(cali,       S_IWUSR | S_IRUGO, show_cali_value,          store_cali_value);
static DRIVER_ATTR(power,                S_IRUGO, show_power_status,          NULL);
static DRIVER_ATTR(selftest,   S_IWUSR | S_IRUGO, show_selftest_value,      store_selftest_value);
static DRIVER_ATTR(firlen,     S_IWUSR | S_IRUGO, show_firlen_value,        store_firlen_value);
static DRIVER_ATTR(trace,      S_IWUSR | S_IRUGO, show_trace_value,         store_trace_value);
static DRIVER_ATTR(status,               S_IRUGO, show_status_value,        NULL);
static DRIVER_ATTR(reg,        S_IWUSR | S_IRUGO, show_reg_value,         store_reg_value);
/*----------------------------------------------------------------------------*/
static struct driver_attribute *sc7660_attr_list[] = {
	&driver_attr_chipinfo,     /*chip information*/
	&driver_attr_sensordata,   /*dump sensor data*/
	&driver_attr_cali,         /*show calibration data*/
	&driver_attr_power,         /*show power reg*/
	&driver_attr_selftest,     /*self control: 0: disable, 1: enable*/
	&driver_attr_firlen,       /*filter length: 0: disable, others: enable*/
	&driver_attr_trace,        /*trace log*/
	&driver_attr_status,   
    &driver_attr_reg,	
};
/*----------------------------------------------------------------------------*/
static int sc7660_create_attr(struct device_driver *driver) 
{
	int idx, err = 0;
	int num = (int)(sizeof(sc7660_attr_list)/sizeof(sc7660_attr_list[0]));
	if (driver == NULL)
	{
		return -EINVAL;
	}

	for(idx = 0; idx < num; idx++)
	{
                err = driver_create_file(driver, sc7660_attr_list[idx]);
		if(err)
		{            
			GSE_ERR("driver_create_file (%s) = %d\n", sc7660_attr_list[idx]->attr.name, err);
			break;
		}
	}    
	return err;
}
/*----------------------------------------------------------------------------*/
static int sc7660_delete_attr(struct device_driver *driver)
{
	int idx ,err = 0;
	int num = (int)(sizeof(sc7660_attr_list)/sizeof(sc7660_attr_list[0]));

	if(driver == NULL)
	{
		return -EINVAL;
	}
	

	for(idx = 0; idx < num; idx++)
	{
		driver_remove_file(driver, sc7660_attr_list[idx]);
	}
	

	return err;
}



/****************************************************************************** 
 * Function Configuration
******************************************************************************/
#if 0
static int sc7660_open(struct inode *inode, struct file *file)
{
	file->private_data = sc7660_i2c_client;

	if(file->private_data == NULL)
	{
		GSE_ERR("null pointer!!\n");
		return -EINVAL;
	}
	return nonseekable_open(inode, file);
}
/*----------------------------------------------------------------------------*/
static int sc7660_release(struct inode *inode, struct file *file)
{
	file->private_data = NULL;
	return 0;
}
#endif

#ifdef CONFIG_COMPAT
static long sc7660_compat_ioctl(struct file *file, unsigned int cmd,
	unsigned long arg)
{
	long err = 0;
	void __user *arg32 = compat_ptr(arg);

	GSE_FUN();
	if (!file->f_op || !file->f_op->unlocked_ioctl)
		return -ENOTTY;

	switch (cmd) {
	case COMPAT_GSENSOR_IOCTL_READ_SENSORDATA:
		if (arg32 == NULL) {
			err = -EINVAL;
			break;
		}
		err = file->f_op->unlocked_ioctl(file, GSENSOR_IOCTL_READ_SENSORDATA, (unsigned long)arg32);
		if (err) {
			GSE_ERR("GSENSOR_IOCTL_READ_SENSORDATA unlocked_ioctl failed.");
			return err;
		}
		break;
	case COMPAT_GSENSOR_IOCTL_SET_CALI:
		if (arg32 == NULL) {
			err = -EINVAL;
			break;
		}

		err = file->f_op->unlocked_ioctl(file, GSENSOR_IOCTL_SET_CALI, (unsigned long)arg32);
		if (err) {
			GSE_ERR("GSENSOR_IOCTL_SET_CALI unlocked_ioctl failed.");
			return err;
		}
		break;
	case COMPAT_GSENSOR_IOCTL_GET_CALI:
		if (arg32 == NULL) {
			err = -EINVAL;
			break;
		}
		err = file->f_op->unlocked_ioctl(file, GSENSOR_IOCTL_GET_CALI, (unsigned long)arg32);
		if (err) {
			GSE_ERR("GSENSOR_IOCTL_GET_CALI unlocked_ioctl failed.");
			return err;
		}
		break;
	case COMPAT_GSENSOR_IOCTL_CLR_CALI:
		if (arg32 == NULL) {
			err = -EINVAL;
			break;
		}
		err = file->f_op->unlocked_ioctl(file, GSENSOR_IOCTL_CLR_CALI, (unsigned long)arg32);
		if (err) {
			GSE_ERR("GSENSOR_IOCTL_CLR_CALI unlocked_ioctl failed.");
			return err;
		}
		break;
	default:
		GSE_ERR("unknown IOCTL: 0x%08x\n", cmd);
		err = -ENOIOCTLCMD;
		break;
	}

	return err;
}
#endif
/*----------------------------------------------------------------------------*/
static long sc7660_unlocked_ioctl(struct file *file, unsigned int cmd, unsigned long arg)       
{
	struct i2c_client *client = sc7660_i2c_client;
	struct sc7660_i2c_data *obj = (struct sc7660_i2c_data*)i2c_get_clientdata(client);	
	char strbuf[SC7660_BUFSIZE];
	void __user *data;
	struct SENSOR_DATA sensor_data;
	int err = 0;
	int cali[3];

//	GSE_FUN();
	if(_IOC_DIR(cmd) & _IOC_READ)
	{
		err = !access_ok(VERIFY_WRITE, (void __user *)arg, _IOC_SIZE(cmd));
	}
	else if(_IOC_DIR(cmd) & _IOC_WRITE)
	{
		err = !access_ok(VERIFY_READ, (void __user *)arg, _IOC_SIZE(cmd));
	}

	if(err)
	{
		GSE_ERR("access error: %08X, (%2d, %2d)\n", cmd, _IOC_DIR(cmd), _IOC_SIZE(cmd));
		return -EFAULT;
	}

	switch(cmd)
	{
		case GSENSOR_IOCTL_INIT:
//			printk("-----lrh-test--sc7660--sc7660_unlocked_ioctl GSENSOR_IOCTL_INIT\n");
			SC7660_Init(client, 0);			
			break;

		case GSENSOR_IOCTL_READ_CHIPINFO:
//			printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_READ_CHIPINFO\n");
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}
			
			SC7660_ReadChipInfo(client, strbuf, SC7660_BUFSIZE);
			if(copy_to_user(data, strbuf, strlen(strbuf)+1))
			{
				err = -EFAULT;
				break;
			}				 
			break;	  

		case GSENSOR_IOCTL_READ_SENSORDATA:
//			printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_READ_SENSORDATA\n");
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}
			
			SC7660_ReadSensorData(client, strbuf, SC7660_BUFSIZE);
			if(copy_to_user(data, strbuf, strlen(strbuf)+1))
			{
				err = -EFAULT;
				break;	  
			}				 
			break;

		case GSENSOR_IOCTL_READ_GAIN:
//		printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_READ_GAIN\n");
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}			
			
			if(copy_to_user(data, &gsensor_gain, sizeof(struct GSENSOR_VECTOR3D)))
			{
				err = -EFAULT;
				break;
			}				 
			break;

		case GSENSOR_IOCTL_READ_OFFSET:
//		printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_READ_OFFSET\n");
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}
			
			/*if(copy_to_user(data, &gsensor_offset, sizeof(struct GSENSOR_VECTOR3D)))
			{
				err = -EFAULT;
				break;
			}*/				 
			break;

		case GSENSOR_IOCTL_READ_RAW_DATA:
//		printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_READ_RAW_DATA\n");
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}
			SC7660_ReadRawData(client, strbuf);
			if(copy_to_user(data, &strbuf, strlen(strbuf)+1))
			{
				err = -EFAULT;
				break;	  
			}
			break;	  

		case GSENSOR_IOCTL_SET_CALI:
//			printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_SET_CALI begin\n");
			data = (void __user*)arg;
			if(data == NULL)
			{
//				printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_SET_CALI data=null\n");
				err = -EINVAL;
				break;	  
			}
			if(copy_from_user(&sensor_data, data, sizeof(sensor_data)))
			{
//				printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_SET_CALI copy from user fail\n");
				err = -EFAULT;
				break;	  
			}
			if(atomic_read(&obj->suspend))
			{
//				printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_SET_CALI sensor suspend\n");
				GSE_ERR("Perform calibration in suspend state!!\n");
				err = -EINVAL;
			}
			else
			{
//				printk("cali data from NVRAM: sc7660x= %d\n sc7660y= %d\n sc7660z= %d\n",sensor_data.x,sensor_data.y,sensor_data.z);
				cali[SC7660_AXIS_X] = sensor_data.x * obj->reso->sensitivity / GRAVITY_EARTH_1000;
				cali[SC7660_AXIS_Y] = sensor_data.y * obj->reso->sensitivity / GRAVITY_EARTH_1000;
				cali[SC7660_AXIS_Z] = sensor_data.z * obj->reso->sensitivity / GRAVITY_EARTH_1000;
							  
				err = SC7660_WriteCalibration(client, cali);			 
				if(err)
				{
//					printk("-----lrh-test--sc7660--sc7660_unlocked_ioctl SC7660_WriteCalibration fail\n");
					break;
				}
			}
//			printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_SET_CALI finish\n");
			break;

		case GSENSOR_IOCTL_CLR_CALI:
//		printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_CLR_CALI\n");
			err = SC7660_ResetCalibration(client);
			break;

		case GSENSOR_IOCTL_GET_CALI:
//		printk("-----lrh-test--sc7660--sc7660_unlocked_ioctl GSENSOR_IOCTL_GET_CALI beigin \n");
			data = (void __user*)arg;
			if(data == NULL)
			{
//				printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_GET_CALI data=null\n");
				err = -EINVAL;
				break;	  
			}
      err = SC7660_ReadCalibration(client, cali);
			if(err)
			{
//					printk("-----lrh-test--sc7660--sc7660_unlocked_ioctl SC7660_ReadCalibration fail\n");
				break;
			}
			
			sensor_data.x = cali[SC7660_AXIS_X] * GRAVITY_EARTH_1000 / obj->reso->sensitivity;
			sensor_data.y = cali[SC7660_AXIS_Y] * GRAVITY_EARTH_1000 / obj->reso->sensitivity;
			sensor_data.z = cali[SC7660_AXIS_Z] * GRAVITY_EARTH_1000 / obj->reso->sensitivity;
//			printk("cali data get from driver save: sc7660x= %d\n sc7660y= %d\n sc7660z= %d\n",sensor_data.x,sensor_data.y,sensor_data.z);
			if(copy_to_user(data, &sensor_data, sizeof(sensor_data)))
			{
//				printk("-----lrh-test--sc7660--sc7660_unlocked_ioct GSENSOR_IOCTL_GET_CALI copy from user fail\n");
				err = -EFAULT;
				break;
			}		
			break;
		

		default:
//			printk("-----lrh-test--sc7660--sc7660_unlocked_ioctl default\n");
			GSE_ERR("unknown IOCTL: 0x%08x\n", cmd);
			err = -ENOIOCTLCMD;
			break;
			
	}

	return err;
}


/*----------------------------------------------------------------------------*/
static struct file_operations sc7660_fops = {
	.owner = THIS_MODULE,
//	.open = sc7660_open,
//	.release = sc7660_release,
	.unlocked_ioctl = sc7660_unlocked_ioctl,
	    #ifdef CONFIG_COMPAT
	.compat_ioctl = sc7660_compat_ioctl,
	#endif
};
/*----------------------------------------------------------------------------*/
static struct miscdevice sc7660_device = {
	.minor = MISC_DYNAMIC_MINOR,
	.name = "gsensor",
	.fops = &sc7660_fops,
};
/*----------------------------------------------------------------------------*/
#ifndef CONFIG_HAS_EARLYSUSPEND
/*----------------------------------------------------------------------------*/
static int sc7660_suspend(struct i2c_client *client, pm_message_t msg) 
{
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);    
	int err = 0;

	GSE_FUN();    

	if(obj == NULL)
	{
		GSE_ERR("null pointer!!\n");
		return -EINVAL;
	}

	atomic_set(&obj->suspend, 1);
        err = SC7660_SetPowerMode(obj->client, false);
	if(err)
	{
		GSE_ERR("write power control fail!!\n");
		return err;
	}

	sensor_power = false;
	
	SC7660_power(obj->hw, 0);

	return 0;
}
/*----------------------------------------------------------------------------*/
static int sc7660_resume(struct i2c_client *client)
{
	struct sc7660_i2c_data *obj = i2c_get_clientdata(client);        
	int err;
	GSE_FUN();

	if(obj == NULL)
	{
		GSE_ERR("null pointer!!\n");
		return -EINVAL;
	}

	SC7660_power(obj->hw, 1);
        err = SC7660_Init(client, 0);
	if(err)
	{
		GSE_ERR("initialize client fail!!\n");
		return err;        
	}
	atomic_set(&obj->suspend, 0);

	return 0;
}
/*----------------------------------------------------------------------------*/
#else /*CONFIG_HAS_EARLY_SUSPEND is defined*/
/*----------------------------------------------------------------------------*/

static void sc7660_early_suspend(struct early_suspend *h) 
{
	struct sc7660_i2c_data *obj = container_of(h, struct sc7660_i2c_data, early_drv);   
	int err;
	GSE_FUN();    

	if(obj == NULL)
	{
		GSE_ERR("null pointer!!\n");
		return;
	}
	atomic_set(&obj->suspend, 1); 
	/*
	if(err = hwmsen_write_byte(obj->client, SC7660_REG_POWER_CTL, 0x00))
	{
		GSE_ERR("write power control fail!!\n");
		return;
	}  
	*/
        err = SC7660_SetPowerMode(obj->client, false);
	if(err)
	{
		GSE_ERR("write power control fail!!\n");
		return;
	}

	sensor_power = false;
	
	SC7660_power(obj->hw, 0);
}
/*----------------------------------------------------------------------------*/
static void sc7660_late_resume(struct early_suspend *h)
{
	struct sc7660_i2c_data *obj = container_of(h, struct sc7660_i2c_data, early_drv);         
	int err;
	GSE_FUN();

	if(obj == NULL)
	{
		GSE_ERR("null pointer!!\n");
		return;
	}

	SC7660_power(obj->hw, 1);
	if(err = SC7660_Init(obj->client, 0))
	{
		GSE_ERR("initialize client fail!!\n");
		return;        
	}
	atomic_set(&obj->suspend, 0);    
}
/*----------------------------------------------------------------------------*/
#endif /*CONFIG_HAS_EARLYSUSPEND*/

// if use  this typ of enable , Gsensor should report inputEvent(x, y, z ,stats, div) to HAL

static int sc7660_open_report_data(int open)
{
	//should queuq work to report event if  is_report_input_direct=true
	return 0;
}

// if use  this typ of enable , Gsensor only enabled but not report inputEvent to HAL

static int sc7660_enable_nodata(int en)
{
    int err = 0;

    mutex_lock(&sc7660_mutex);
	if(((en == 0) && (sensor_power == false)) ||((en == 1) && (sensor_power == true)))
	{
		enable_status = sensor_power;
		GSE_LOG("Gsensor device have updated!\n");
	}
	else
	{
		enable_status = !sensor_power;
		if (atomic_read(&obj_i2c_data->suspend) == 0)
		{

			err = SC7660_SetPowerMode(obj_i2c_data->client, enable_status);

			GSE_LOG("Gsensor not in suspend KXTJ2_1009_SetPowerMode!, enable_status = %d\n",enable_status);
		}
		else
		{
			GSE_LOG("Gsensor in suspend and can not enable or disable!enable_status = %d\n",enable_status);
		}
	}
	mutex_unlock(&sc7660_mutex);

    if(err != SC7660_SUCCESS)
	{
		printk("kxtj2_1009_enable_nodata fail!\n");
		return -1;
	}

    printk("kxtj2_1009_enable_nodata OK!\n");
	return 0;
}

static int sc7660_set_delay(u64 ns)
{
    int err = 0;
    int value;

	int sample_delay;


    value = (int)ns/1000/1000;
   
	if(value <= 5)
	{
		sample_delay = SC7660_BW_200HZ;
	}
	else if(value <= 10)
	{
		sample_delay = SC7660_BW_100HZ;
	}
	else
	{
		sample_delay = SC7660_BW_50HZ;
	}

	mutex_lock(&sc7660_mutex);
	err = SC7660_SetBWRate(obj_i2c_data->client, sample_delay);
	mutex_unlock(&sc7660_mutex);
	if(err != SC7660_SUCCESS ) //0x2C->BW=100Hz
	{
		GSE_ERR("Set delay parameter error!\n");
        return -1;
	}

    GSE_LOG("sc7660_set_delay (%d)\n",value);

	return 0;
}


static int sc7660_get_data(int* x ,int* y,int* z, int* status)
{

	char buff[SC7660_BUFSIZE];

    mutex_lock(&sc7660_mutex);
	SC7660_ReadSensorData(obj_i2c_data->client, buff, SC7660_BUFSIZE);
	mutex_unlock(&sc7660_mutex);
	sscanf(buff, "%x %x %x", x, y, z);				
	*status = SENSOR_STATUS_ACCURACY_MEDIUM;

	return 0;
}


/*----------------------------------------------------------------------------*/
static int sc7660_i2c_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	struct sc7660_i2c_data *obj;
	//struct hwmsen_object sobj;
	
    struct acc_control_path ctl={0};
    struct acc_data_path data={0};
	int err = 0;

	GSE_FUN();

    obj = kzalloc(sizeof(*obj), GFP_KERNEL);
	if(!(obj))
	{
		err = -ENOMEM;
		goto exit;
	}
	
	memset(obj, 0, sizeof(struct sc7660_i2c_data));


        obj->hw = hw;
	
        err = hwmsen_get_convert(obj->hw->direction, &obj->cvt);
	if(err)
	{
		GSE_ERR("invalid direction: %d\n", obj->hw->direction);
		goto exit;
	}

	client->addr = 0x1d;  // 0x19   
	
	obj_i2c_data = obj;
	obj->client = client;
	sc7660_i2c_client = client;	
	i2c_set_clientdata(client,obj);
	
	atomic_set(&obj->trace, 0);
	atomic_set(&obj->suspend, 0);
	
#ifdef CONFIG_SC7660_LOWPASS
	if(obj->hw->firlen > C_MAX_FIR_LENGTH)
	{
		atomic_set(&obj->firlen, C_MAX_FIR_LENGTH);
	}	
	else
	{
		atomic_set(&obj->firlen, obj->hw->firlen);
	}
	
	if(atomic_read(&obj->firlen) > 0)
	{
		atomic_set(&obj->fir_en, 1);
	}
	
#endif

        err = SC7660_Init(client, 1);
	if(err)
	{
        GSE_ERR("chip init failed !\n");
		goto exit_init_failed;
	}
	
        err = misc_register(&sc7660_device);
	if(err)
	{
		GSE_ERR("sc7660_device register failed\n");
		goto exit_misc_device_register_failed;
	}

        err = sc7660_create_attr(&(sc7660_init_info.platform_diver_addr->driver));
        if(err)
	{
		GSE_ERR("create attribute err = %d\n", err);
		goto exit_create_attr_failed;
	}

    ctl.is_use_common_factory = false;
	ctl.open_report_data= sc7660_open_report_data;
	ctl.enable_nodata = sc7660_enable_nodata;
	ctl.set_delay  = sc7660_set_delay;
	ctl.is_report_input_direct = false;
	ctl.is_support_batch = obj->hw->is_batch_supported;
	err = acc_register_control_path(&ctl);
	if(err)
	{
	 	GSE_ERR("register acc control path err\n");
		goto exit_kfree;
	}

	data.get_data = sc7660_get_data;
	data.vender_div = 1000;
	err = acc_register_data_path(&data);
	if(err)
	{
	 	GSE_ERR("register acc data path err\n");
		goto exit_kfree;
	}

#ifdef CONFIG_HAS_EARLYSUSPEND
	obj->early_drv.level    = EARLY_SUSPEND_LEVEL_DISABLE_FB - 1,
	obj->early_drv.suspend  = sc7660_early_suspend,
	obj->early_drv.resume   = sc7660_late_resume,    
	register_early_suspend(&obj->early_drv);
#endif 
    sc7660_init_flag = 0;
  
	GSE_LOG("%s: OK\n", __func__);    
	return 0;

	exit_create_attr_failed:
	misc_deregister(&sc7660_device);
	exit_misc_device_register_failed:
	exit_init_failed:
	//i2c_detach_client(client);
	exit_kfree:
	kfree(obj);
	exit:
	GSE_ERR("%s: err = %d\n", __func__, err);        
    sc7660_init_flag =-1;
	return err;
}

/*----------------------------------------------------------------------------*/
static int sc7660_i2c_remove(struct i2c_client *client)
{
	int err = 0;	
	
        err = sc7660_delete_attr(&(sc7660_init_info.platform_diver_addr->driver));
	if(err)
	{
		GSE_ERR("sc7660_delete_attr fail: %d\n", err);
	}
	
        err = misc_deregister(&sc7660_device);
	if(err)
	{
		GSE_ERR("misc_deregister fail: %d\n", err);
	}

        err = hwmsen_detach(ID_ACCELEROMETER);
	if(err) {
		GSE_ERR("hwmsen_detach fail: %d\n", err);
        }	

	sc7660_i2c_client = NULL;
	i2c_unregister_device(client);
	kfree(i2c_get_clientdata(client));
	return 0;
}

/*----------------------------------------------------------------------------*/
static int sc7660_remove(void)
{
    GSE_FUN();    

    SC7660_power(hw, 0);    

    i2c_del_driver(&sc7660_i2c_driver);
	
    return 0;
}
/*----------------------------------------------------------------------------*/


/*----------------------------------------------------------------------------*/

static int  sc7660_local_init(void)
{
    GSE_FUN();

    SC7660_power(hw, 1);

    if(i2c_add_driver(&sc7660_i2c_driver))
    {
        GSE_ERR("add driver error\n");
        return -1;
    }

    if(-1 == sc7660_init_flag)
    {
	return -1;
    }		

    return 0;
}


static int __init sc7660_init(void)
{
    const char *name = "mediatek,sc7660";
   
    GSE_FUN();

    hw = get_accel_dts_func(name, hw);
    if (!hw) {
	GSE_ERR("get dts info fail\n");
    }

    GSE_LOG("%s: i2c_number:%d, i2c_addr:0x%02x\n", __func__,hw->i2c_num,hw->i2c_addr[0]);  // 2   0x19

    acc_driver_add(&sc7660_init_info);

    return 0;
}
/*----------------------------------------------------------------------------*/
static void __exit sc7660_exit(void)
{
	GSE_FUN();
}
/*----------------------------------------------------------------------------*/
module_init(sc7660_init);
module_exit(sc7660_exit);
/*----------------------------------------------------------------------------*/
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("SC7660 I2C driver");
MODULE_AUTHOR("Chunlei.Wang@mediatek.com");
MODULE_VERSION("1.0");
