package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

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
    public void should_create_load_file_from_utxos_in_multiple_files() throws Exception {
        final UtxoCommitmentLoader utxoCommitmentLoader = new UtxoCommitmentLoader();

        final HashSet<String> expectedResults = new HashSet<>();
        {
            expectedResults.add("6E544DBDAA5FC7CCF14861DF06366C5F86FDC2CEDCAFA44C35B807CE3C010000\t1\t468349\t0\t14250000\t76A914865E218FF25929EEE880E0E3B6F95280B2D0544388AC");
            expectedResults.add("0DC11E98B05483D511FE59FD4F5970C8B5A3EB1CDF35E900B07C995558040000\t1\t540226\t0\t2609705\t76A914CA71A5B1BC3CD755EBD12949F0FE99608C61879588AC");
            expectedResults.add("A53421B937BE7BFE89EF6CC3F4124706B560AF393B527E3E3D9D0C285B050000\t0\t462073\t0\t2789\t76A914D7270DD4248ECA877673637412A8C1B8CF1FCC5C88AC");
            expectedResults.add("D15824DC504A0C5BD5CF94E2F5EB096F09F72D9739F0769A327A528A75050000\t1\t629949\t0\t1187\t76A9143FECC44FFAC592CCCA75627A4DFB14B082CA91C288AC");
            expectedResults.add("E1C9467A885A156E56A29D9C854E65674D581AD75611B02290454B4862060000\t1\t189808\t0\t9466355\t76A914D957C2536C205E2483B635CE17B2E02036788D5488AC");
        }

        final MutableList<File> inputFiles = new MutableList<>();
        final ByteArray[] utxoSets = new ByteArray[] {
            ByteArray.fromHexString("0000013CCE07B8354CA4AFDCCEC2FD865F6C3606DF6148F1CCC75FAABD4D546E010000007D2507001070D900000000001900000076A914865E218FF25929EEE880E0E3B6F95280B2D0544388AC"),
            ByteArray.fromHexString("0000045855997CB000E935DF1CEBA3B5C870594FFD59FE11D58354B0981EC10D01000000423E080029D22700000000001900000076A914CA71A5B1BC3CD755EBD12949F0FE99608C61879588AC"),
            ByteArray.fromHexString("0000055B280C9D3D3E7E523B39AF60B5064712F4C36CEF89FE7BBE37B92134A500000000F90C0700E50A0000000000001900000076A914D7270DD4248ECA877673637412A8C1B8CF1FCC5C88AC"),
            ByteArray.fromHexString("000005758A527A329A76F039972DF7096F09EBF5E294CFD55B0C4A50DC2458D101000000BD9C0900A3040000000000001900000076A9143FECC44FFAC592CCCA75627A4DFB14B082CA91C288AC"),
            ByteArray.fromHexString("00000662484B459022B01156D71A584D67654E859C9DA2566E155A887A46C9E10100000070E50200F3719000000000001900000076A914D957C2536C205E2483B635CE17B2E02036788D5488AC")
        };

        for (final ByteArray byteArray : utxoSets) {
            final File file = File.createTempFile("utxo-in", ".dat");
            file.deleteOnExit();

            try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                fileOutputStream.write(byteArray.getBytes());
                fileOutputStream.flush();
            }
            inputFiles.add(file);
        }

        for (final File file : inputFiles) {
            System.out.println("File: " + file.getPath() + " " + IoUtil.getFileContents(file).length + " bytes");
        }

        final File file = File.createTempFile("utxo-out", ".dat");
        file.deleteOnExit();

        utxoCommitmentLoader.createLoadFile(inputFiles, file);

        final String fileContents = StringUtil.bytesToString(IoUtil.getFileContents(file));
        for (final String line : fileContents.split("\n")) {
            expectedResults.remove(line);
        }
        Assert.assertTrue(expectedResults.isEmpty());
    }
}
