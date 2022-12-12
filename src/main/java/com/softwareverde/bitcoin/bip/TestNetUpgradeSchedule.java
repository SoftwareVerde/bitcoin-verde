package com.softwareverde.bitcoin.bip;

public class TestNetUpgradeSchedule extends AbstractUpgradeSchedule {
    public TestNetUpgradeSchedule() {
        _activationBlockHeights[UpgradeHeight.BIP16_ACTIVATION_BLOCK_HEIGHT.value] = 514L;
        _activationBlockHeights[UpgradeHeight.BIP34_ACTIVATION_BLOCK_HEIGHT.value] = 21111L;
        _activationBlockHeights[UpgradeHeight.BIP65_ACTIVATION_BLOCK_HEIGHT.value] = 581885L;
        _activationBlockHeights[UpgradeHeight.BIP66_ACTIVATION_BLOCK_HEIGHT.value] = 330776L;
        _activationBlockHeights[UpgradeHeight.BIP68_ACTIVATION_BLOCK_HEIGHT.value] = 770112L;
        _activationBlockHeights[UpgradeHeight.BIP112_ACTIVATION_BLOCK_HEIGHT.value] = 770112L;
        _activationBlockHeights[UpgradeHeight.BIP113_ACTIVATION_BLOCK_HEIGHT.value] = 770112L;
        _activationBlockHeights[UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT.value] = 1155876L; // a.k.a. UAHF
        _activationBlockHeights[UpgradeHeight.HF20171113_ACTIVATION_BLOCK_HEIGHT.value] = 1188698L;
        _activationBlockHeights[UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT.value] = 1267997L;

        _activationBlockTimes[UpgradeTime.HF20190515_ACTIVATION_TIME.value] = 1557921600L;
        _activationBlockTimes[UpgradeTime.HF20191115_ACTIVATION_TIME.value] = 1573820238L;
        _activationBlockTimes[UpgradeTime.HF20200515_ACTIVATION_TIME.value] = 1589544294L;
        _activationBlockTimes[UpgradeTime.HF20201115_ACTIVATION_TIME.value] = 1605441600L;
        _activationBlockTimes[UpgradeTime.HF20220515_ACTIVATION_TIME.value] = 1637694000L;
        _activationBlockTimes[UpgradeTime.HF20230515_ACTIVATION_TIME.value] = 1684152000L;
    }
}
