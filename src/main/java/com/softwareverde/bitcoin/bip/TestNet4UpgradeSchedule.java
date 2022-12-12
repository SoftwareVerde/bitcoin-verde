package com.softwareverde.bitcoin.bip;

public class TestNet4UpgradeSchedule extends AbstractUpgradeSchedule {
    public TestNet4UpgradeSchedule() {
        _activationBlockHeights[UpgradeHeight.BIP16_ACTIVATION_BLOCK_HEIGHT.value] = 2L;
        _activationBlockHeights[UpgradeHeight.BIP34_ACTIVATION_BLOCK_HEIGHT.value] = 3L;
        _activationBlockHeights[UpgradeHeight.BIP65_ACTIVATION_BLOCK_HEIGHT.value] = 4L;
        _activationBlockHeights[UpgradeHeight.BIP66_ACTIVATION_BLOCK_HEIGHT.value] = 5L;
        _activationBlockHeights[UpgradeHeight.BIP68_ACTIVATION_BLOCK_HEIGHT.value] = 6L;
        _activationBlockHeights[UpgradeHeight.BIP112_ACTIVATION_BLOCK_HEIGHT.value] = 6L;
        _activationBlockHeights[UpgradeHeight.BIP113_ACTIVATION_BLOCK_HEIGHT.value] = 6L;
        _activationBlockHeights[UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT.value] = 7L; // a.k.a. UAHF
        _activationBlockHeights[UpgradeHeight.HF20171113_ACTIVATION_BLOCK_HEIGHT.value] = 3001L;
        _activationBlockHeights[UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT.value] = 4001L;

        _activationBlockTimes[UpgradeTime.HF20190515_ACTIVATION_TIME.value] = 0L;
        _activationBlockTimes[UpgradeTime.HF20191115_ACTIVATION_TIME.value] = 1599487350L;
        _activationBlockTimes[UpgradeTime.HF20200515_ACTIVATION_TIME.value] = 0L;
        _activationBlockTimes[UpgradeTime.HF20201115_ACTIVATION_TIME.value] = 1605441600L;
        _activationBlockTimes[UpgradeTime.HF20220515_ACTIVATION_TIME.value] = 1637694000L;
        _activationBlockTimes[UpgradeTime.HF20230515_ACTIVATION_TIME.value] = 1684152000L;
    }
}
