package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteBuffer;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class UtxoCommitmentLoaderTests extends UnitTest {
    @Before @Override
    public void before() throws Exception {
        super.before();

        Logger.setLogLevel("com.softwareverde.cryptography.secp256k1", LogLevel.ERROR); // TODO: Remove
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void test() throws Exception {
        final UtxoCommitmentLoader utxoCommitmentLoader = new UtxoCommitmentLoader();

        final MutableList<File> inputFiles = new MutableList<>();
        inputFiles.add(new File("/Users/green/development/bitcoin-verde/utxo.dat"));
//        final ByteArray[] utxoSets = new ByteArray[] {
//            ByteArray.fromHexString("982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E00000000010000000100F2052A0100004100000096B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC")
//        };
//
//        final ByteBuffer concatenatedUtxoSets = new ByteBuffer();
//        for (final ByteArray byteArray : utxoSets) {
//            concatenatedUtxoSets.appendBytes(byteArray.getBytes(), byteArray.getByteCount());
//        }
//
//        final int totalByteCountToWrite = concatenatedUtxoSets.getByteCount();
//        int bytesPerFile = (totalByteCountToWrite / 2);
//        int readIndex = 0;
//        while (readIndex < totalByteCountToWrite) {
//            final File file = File.createTempFile("utxo-in", ".dat");
//            file.deleteOnExit();
//
//            try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
//                fileOutputStream.write(concatenatedUtxoSets.readBytes(bytesPerFile));
//                fileOutputStream.flush();
//            }
//
//            readIndex += bytesPerFile;
//            bytesPerFile = Math.max(1, (bytesPerFile * 7 / 13));
//            inputFiles.add(file);
//        }

        for (final File file : inputFiles) {
            // System.out.println("File: " + file.getPath() + " " + IoUtil.getFileContents(file).length + " bytes - " + HexUtil.toHexString(IoUtil.getFileContents(file)));
            System.out.println("File: " + file.getPath());
        }

        final File file = new File("/Users/green/development/bitcoin-verde/utxo-out.csv"); // File.createTempFile("utxo-out", ".dat");
        // file.deleteOnExit();

        utxoCommitmentLoader.createLoadFile(inputFiles, file);

        // System.out.println(StringUtil.bytesToString(IoUtil.getFileContents(file)));
    }
}
