package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.network.time.VolatileNetworkTime;

public class FakeBlockHeaderValidatorContext extends FakeDifficultyCalculatorContext implements BlockHeaderValidator.Context {
    protected final VolatileNetworkTime _networkTime;

    public FakeBlockHeaderValidatorContext(final VolatileNetworkTime networkTime, final UpgradeSchedule upgradeSchedule) {
        super(upgradeSchedule);
        _networkTime = networkTime;
    }

    @Override
    public VolatileNetworkTime getNetworkTime() {
        return _networkTime;
    }
}
