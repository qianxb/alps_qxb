/* linux/drivers/hwmon/sc7a20.c
 *
 * (C) Copyright 2008 
 * MediaTek <www.mediatek.com>
 *
 * SC7660 driver for MT6516
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
#ifndef SC7660_H
#define SC7660_H
	 
#include <linux/ioctl.h>
#include <linux/kernel.h>
#define MTK_ANDROID_M          	1 

#define SC7660_I2C_SLAVE_ADDR		0x32  //0x30
#define SC7660_FIXED_DEVID			0x11	 
	 /* SC7660 Register Map  (Please refer to SC7660 Specifications) */
#define SC7660_REG_CTL_REG1		0x20
#define SC7660_REG_CTL_REG2		0x21
#define SC7660_REG_CTL_REG3		0x22
#define SC7660_REG_CTL_REG4     0x23

#define SC7660_REG_DATAX0		    0x28
#define SC7660_REG_OUT_X		    0x29
#define SC7660_REG_OUT_Y		    0x2B
#define SC7660_REG_OUT_Z		    0x2D

#define SC7660_REG_DEVID			0x0F
#define SC7660_BW	            0xf0
#define SC7660_BW_400HZ			0x70 //400 or 100 on other choise //changed
#define SC7660_BW_200HZ			0x60
#define SC7660_BW_100HZ			0x50
#define SC7660_BW_50HZ			0x40
#define	SC7660_FULLRANG_LSB		0XFF
	 
#define SC7660_MEASURE_MODE		0xf0	//changed 
#define SC7660_ACTIVE_MODE      0x50
#define SC7660_DATA_READY			0x07    //changed
#define SC7660_RANGE			0x30   //8g or 2g no ohter choise//changed
#define SC7660_RANGE_8G			0x20 
#define SC7660_RANGE_2G			0x00 	 
#define SC7660_SELF_TEST           0x04 //changed
	 
#define SC7660_STREAM_MODE			0x80
#define SC7660_SAMPLES_15			0x0F
	 
#define SC7660_FS_8G_LSB_G			64
#define SC7660_FS_4G_LSB_G			128
#define SC7660_FS_2G_LSB_G			256
	 
#define SC7660_LEFT_JUSTIFY		0x04
#define SC7660_RIGHT_JUSTIFY		0x00
	 
	 
#define SC7660_SUCCESS						0
#define SC7660_ERR_I2C						-1
#define SC7660_ERR_STATUS					-3
#define SC7660_ERR_SETUP_FAILURE			-4
#define SC7660_ERR_GETGSENSORDATA			-5
#define SC7660_ERR_IDENTIFICATION			-6
	 
	 
	 
#define SC7660_BUFSIZE				256
	 
#endif

