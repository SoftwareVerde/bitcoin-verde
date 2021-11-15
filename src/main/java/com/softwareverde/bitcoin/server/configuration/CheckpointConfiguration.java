package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class CheckpointConfiguration {
    protected final HashMap<Long, Sha256Hash> _checkpoints = new HashMap<>();

    public CheckpointConfiguration() {
        _checkpoints.put(11111L,  Sha256Hash.fromHexString("0000000069E244F73D78E8FD29BA2FD2ED618BD6FA2EE92559F542FDB26E7C1D"));
        _checkpoints.put(33333L,  Sha256Hash.fromHexString("000000002DD5588A74784EAA7AB0507A18AD16A236E7B1CE69F00D7DDFB5D0A6"));
        _checkpoints.put(74000L,  Sha256Hash.fromHexString("0000000000573993A3C9E41CE34471C079DCF5F52A0E824A81E7F953B8661A20"));
        _checkpoints.put(105000L, Sha256Hash.fromHexString("00000000000291CE28027FAEA320C8D2B054B2E0FE44A773F3EEFB151D6BDC97"));
        _checkpoints.put(134444L, Sha256Hash.fromHexString("00000000000005B12FFD4CD315CD34FFD4A594F430AC814C91184A0D42D2B0FE"));
        _checkpoints.put(168000L, Sha256Hash.fromHexString("000000000000099E61EA72015E79632F216FE6CB33D7899ACB35B75C8303B763"));
        _checkpoints.put(193000L, Sha256Hash.fromHexString("000000000000059F452A5F7340DE6682A977387C17010FF6E6C3BD83CA8B1317"));
        _checkpoints.put(210000L, Sha256Hash.fromHexString("000000000000048B95347E83192F69CF0366076336C639F9B7228E9BA171342E"));
        _checkpoints.put(216116L, Sha256Hash.fromHexString("00000000000001B4F4B433E81EE46494AF945CF96014816A4E2370F11B23DF4E"));
        _checkpoints.put(225430L, Sha256Hash.fromHexString("00000000000001C108384350F74090433E7FCF79A606B8E797F065B130575932"));
        _checkpoints.put(250000L, Sha256Hash.fromHexString("000000000000003887DF1F29024B06FC2200B55F8AF8F35453D7BE294DF2D214"));
        _checkpoints.put(279000L, Sha256Hash.fromHexString("0000000000000001AE8C72A0B0C301F67E3AFCA10E819EFA9041E458E9BD7E40"));
        _checkpoints.put(295000L, Sha256Hash.fromHexString("00000000000000004D9B4EF50F0F9D686FD69DB2E03AF35A100370C64632A983"));
        _checkpoints.put(478558L, Sha256Hash.fromHexString("0000000000000000011865AF4122FE3B144E2CBEEA86142E8FF2FB4107352D43"));
        _checkpoints.put(504031L, Sha256Hash.fromHexString("0000000000000000011EBF65B60D0A3DE80B8175BE709D653B4C1A1BEEB6AB9C"));
        _checkpoints.put(530359L, Sha256Hash.fromHexString("0000000000000000011ADA8BD08F46074F44A8F155396F43E38ACF9501C49103"));
        _checkpoints.put(556767L, Sha256Hash.fromHexString("0000000000000000004626FF6E3B936941D341C5932ECE4357EECCAC44E6D56C"));
        _checkpoints.put(582680L, Sha256Hash.fromHexString("000000000000000001B4B8E36AEC7D4F9671A47872CB9A74DC16CA398C7DCC18"));
        _checkpoints.put(609136L, Sha256Hash.fromHexString("000000000000000000B48BB207FAAC5AC655C313E41AC909322EAA694F5BC5B1"));
        _checkpoints.put(635259L, Sha256Hash.fromHexString("00000000000000000033DFEF1FC2D6A5D5520B078C55193A9BF498C5B27530F7"));
    }

    public Boolean violatesCheckpoint(final Long blockHeight, final Sha256Hash blockHash) {
        final Sha256Hash requiredBlockHash = _checkpoints.get(blockHeight);
        if (requiredBlockHash == null) { return false; } // Not a checkpoint block...

        return (! Util.areEqual(requiredBlockHash, blockHash));
    }
}
