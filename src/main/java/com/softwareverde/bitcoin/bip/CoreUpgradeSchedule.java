package com.softwareverde.bitcoin.bip;

public class CoreUpgradeSchedule extends AbstractUpgradeSchedule {
    public CoreUpgradeSchedule() {
        _activationBlockHeights[UpgradeHeight.BIP16_ACTIVATION_BLOCK_HEIGHT.value] = 173805L; // Pay to Script Hash - https://github.com/bitcoin/bips/blob/master/bip-0016.mediawiki
        _activationBlockHeights[UpgradeHeight.BIP34_ACTIVATION_BLOCK_HEIGHT.value] = 227836L; // Block v2. Height in Coinbase - https://github.com/bitcoin/bips/blob/master/bip-0034.mediawiki
        _activationBlockHeights[UpgradeHeight.BIP65_ACTIVATION_BLOCK_HEIGHT.value] = 388167L; // OP_CHECKLOCKTIMEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki
        _activationBlockHeights[UpgradeHeight.BIP66_ACTIVATION_BLOCK_HEIGHT.value] = 363725L; // Strict DER signatures -- https://github.com/bitcoin/bips/blob/master/bip-0066.mediawiki
        _activationBlockHeights[UpgradeHeight.BIP68_ACTIVATION_BLOCK_HEIGHT.value] = 419328L; // Relative Lock-Time Using Consensus-Enforced Sequence Numbers - https://github.com/bitcoin/bips/blob/master/bip-0068.mediawiki
        _activationBlockHeights[UpgradeHeight.BIP112_ACTIVATION_BLOCK_HEIGHT.value] = 419328L; // OP_CHECKSEQUENCEVERIFY -- https://github.com/bitcoin/bips/blob/master/bip-0112.mediawiki
        _activationBlockHeights[UpgradeHeight.BIP113_ACTIVATION_BLOCK_HEIGHT.value] = 419328L; // Median Time-Past As Endpoint For LockTime Calculations -- https://github.com/bitcoin/bips/blob/master/bip-0113.mediawiki
        _activationBlockHeights[UpgradeHeight.BUIP55_ACTIVATION_BLOCK_HEIGHT.value] = 478559L; // Bitcoin Cash: UAHF - https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/uahf-technical-spec.md
        _activationBlockHeights[UpgradeHeight.HF20171113_ACTIVATION_BLOCK_HEIGHT.value] = 504032L; // Bitcoin Cash: 2017-11-07 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/nov-13-hardfork-spec.md
        _activationBlockHeights[UpgradeHeight.HF20180515_ACTIVATION_BLOCK_HEIGHT.value] = 530359L; // Bitcoin Cash: 2018-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/may-2018-hardfork.md
        _activationBlockHeights[UpgradeHeight.HF20181115_ACTIVATION_BLOCK_HEIGHT.value] = 556767L; // Bitcoin Cash: 2018-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2018-nov-upgrade.md

        _activationBlockTimes[UpgradeTime.HF20190515_ACTIVATION_TIME.value] = 1557921600L; // Bitcoin Cash: 2019-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-05-15-upgrade.md
        _activationBlockTimes[UpgradeTime.HF20191115_ACTIVATION_TIME.value] = 1573819200L; // Bitcoin Cash: 2019-11-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2019-11-15-upgrade.md
        _activationBlockTimes[UpgradeTime.HF20200515_ACTIVATION_TIME.value] = 1589544000L; // Bitcoin Cash: 2020-05-15 Hard Fork:  https://github.com/bitcoincashorg/bitcoincash.org/blob/master/spec/2020-05-15-upgrade.md
        _activationBlockTimes[UpgradeTime.HF20201115_ACTIVATION_TIME.value] = 1605441600L; // Bitcoin Cash: 2020-11-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2020-11-15-upgrade.md
        _activationBlockTimes[UpgradeTime.HF20220515_ACTIVATION_TIME.value] = 1652616000L; // Bitcoin Cash: 2022-05-15 Hard Fork:  https://gitlab.com/bitcoin-cash-node/bchn-sw/bitcoincash-upgrade-specifications/-/blob/master/spec/2022-05-15-upgrade.md
        _activationBlockTimes[UpgradeTime.HF20230515_ACTIVATION_TIME.value] = 1684152000L; // Bitcoin Cash: 2023-05-15 Hard Fork: https://bitcoincashresearch.org/t/2021-bch-upgrade-items-brainstorm/130/29 // TODO: Update upgrade spec.
    }
}
