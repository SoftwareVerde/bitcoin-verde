package com.softwareverde.bitcoin.server.message.type.dsproof;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DoubleSpendProofMessageTests extends UnitTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception{
        super.after();
    }

    @Test
    public void should_inflate_double_spend_proof_message() throws Exception {
        // Setup
        final DoubleSpendProofMessageInflater doubleSpendProofMessageInflater = new DoubleSpendProofMessageInflater();

        //                                                 (HEADER)                                             (PAYLOAD)
        final ByteArray payload = ByteArray.fromHexString("E3E1F3E8647370726F6F662D6265746190010000AEFF2EC7" + "0B9D14B709AA59BD594EDCA17DB2951C6660EBC8DAA31CEAE233A5550314F1580000000001000000FFFFFFFF0000000037FC0D39E5FE250B5DE0F33D0FB454F532F1F96B26895C16961CD647A524CF1C3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E706650447DBAD1250B871EE78B8DCD80C8F33F4DDE3917A38D4FCF8094352BECDA7F0C0901483045022100B34A120E69BC933AE16C10DB0F565CB2DA1B80A9695A51707E8A80C9AA5C22BF02206C390CB328763AB9AB2D45F874D308AF2837D6D8CFC618AF76744B9EEB69C3934101000000FFFFFFFF0000000037FC0D39E5FE250B5DE0F33D0FB454F532F1F96B26895C16961CD647A524CF1C3BB13029CE7B1F559EF5E747FCAC439F1455A2EC7C5F09B72290795E706650440E2988B0EC1C79B25CFF60F2B52E08F77076E42A87E153F7D38E1A61857A2F7A01483045022100D9D22406611228D64E6B674DE8B16E802F8F789F8338130506C7741CDAE9116602202DC63A4F5F9E750EEC9DFC1557469BDA43D3491B358484E5C25992A381048A4941");

        // Action
        final DoubleSpendProofMessage doubleSpendProofMessage = doubleSpendProofMessageInflater.fromBytes(payload.getBytes());
        final ByteArray deflatedBytes = doubleSpendProofMessage.getBytes();

        // Assert
        Assert.assertNotNull(doubleSpendProofMessage);
        Assert.assertEquals(payload, deflatedBytes);
    }
}
