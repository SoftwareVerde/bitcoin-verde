package com.softwareverde.bitcoin.bip;

public class ChipNetUpgradeSchedule extends TestNet4UpgradeSchedule {
    public ChipNetUpgradeSchedule() {
        super();

        _activationBlockTimes[UpgradeTime.HF20230515_ACTIVATION_TIME.value] = 1668513600L; // September: 1662250000L (TempNet)
    }
}
