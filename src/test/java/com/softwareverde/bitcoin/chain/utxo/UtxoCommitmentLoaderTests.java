package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.MultisetHashTests;
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
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void test() throws Exception {
        //                                                                                                              96B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000
        // 982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E 00000000 01000000 0100F2052A010000 00434104 96B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC
        // 982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E 00000000 01000000 0100F2052A010000 00434104 96B538E853519C726A2C91E61E
        // 982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E 00000000 01000000 0100F2052A010000 00434104

        final UtxoCommitmentLoader utxoCommitmentLoader = new UtxoCommitmentLoader();

        final int bytesPerFile = 65;

        final MutableList<File> inputFiles = new MutableList<>();
        final ByteArray[] utxoSets = new ByteArray[] { MultisetHashTests.D1_BYTES, MultisetHashTests.D2_BYTES, MultisetHashTests.D3_BYTES };

        int fileByteCount = 0;
        FileOutputStream fileOutputStream = null;
        for (final ByteArray utxoSetBytes : utxoSets) {
            int readIndex = 0;
            while (readIndex < utxoSetBytes.getByteCount()) {
                if (fileByteCount == 0) {
                    if (fileOutputStream != null) {
                        fileOutputStream.flush();
                        fileOutputStream.close();
                    }

                    final File file = File.createTempFile("utxo-in", ".dat");
                    file.deleteOnExit();

                    fileOutputStream = new FileOutputStream(file);
                    inputFiles.add(file);
                }

                fileOutputStream.write(utxoSetBytes.getByte(readIndex));

                fileByteCount = ((fileByteCount + 1) % bytesPerFile);
                readIndex += 1;
            }
        }
        if (fileOutputStream != null) {
            fileOutputStream.close();
        }

        for (final File file : inputFiles) {
            System.out.println("File: " + file.getPath() + " " + IoUtil.getFileContents(file).length + " bytes - " + HexUtil.toHexString(IoUtil.getFileContents(file)));
        }

        final File file = File.createTempFile("utxo-out", ".dat");
        file.deleteOnExit();

        utxoCommitmentLoader.createLoadFile(inputFiles, file);

        System.out.println(StringUtil.bytesToString(IoUtil.getFileContents(file)));
    }
}
