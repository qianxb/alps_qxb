package com.mediatek.usp;

/** {@hide} */
interface IUspService
{
    String getActiveOpPack();
    String getOpPackFromSimInfo(String mcc_mnc);
    void setOpPackActive(String opPack);
    Map getAllOpPackList();
}