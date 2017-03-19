package tomograph;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import org.apache.commons.cli.*;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.util.UIDUtils;

import java.io.*;
import java.util.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.util.UIDUtils;

public class Jpg2Dcm {
    private static final String USAGE = "jpg2dcm [Options] <jpgfile> <dcmfile>";
    private static final String DESCRIPTION = "Encapsulate JPEG Image into DICOM Object.\nOptions:";
    private static final String EXAMPLE = "--\nExample 1: Encapulate JPEG Image verbatim with default values for mandatory DICOM attributes into DICOM Secondary Capture Image:\n$ jpg2dcm image.jpg image.dcm\n--\nExample 2: Encapulate JPEG Image without application segments and additional DICOM attributes to mandatory defaults into DICOM Image Object:\n$ jpg2dcm --no-appn -c patattrs.cfg homer.jpg image.dcm\n--\nExample 3: Encapulate MPEG2 Video with specified DICOM attributes into DICOM Video Object:\n$ jpg2dcm --mpeg -C mpg2dcm.cfg video.mpg video.dcm";
    private static final String LONG_OPT_CHARSET = "charset";
    private static final String OPT_CHARSET_DESC = "Specific Character Set code string, ISO_IR 100 by default";
    private static final String OPT_AUGMENT_CONFIG_DESC = "Specifies DICOM attributes included additional to mandatory defaults";
    private static final String OPT_REPLACE_CONFIG_DESC = "Specifies DICOM attributes included instead of mandatory defaults";
    private static final String LONG_OPT_TRANSFER_SYNTAX = "transfer-syntax";
    private static final String OPT_TRANSFER_SYNTAX_DESC = "Transfer Syntax; 1.2.840.10008.1.2.4.50 (JPEG Baseline) by default.";
    private static final String LONG_OPT_MPEG = "mpeg";
    private static final String OPT_MPEG_DESC = "Same as --transfer-syntax 1.2.840.10008.1.2.4.100 (MPEG2).";
    private static final String LONG_OPT_UID_PREFIX = "uid-prefix";
    private static final String OPT_UID_PREFIX_DESC = "Generate UIDs with given prefix, 1.2.40.0.13.1.<host-ip> by default.";
    private static final String LONG_OPT_NO_APPN = "no-appn";
    private static final String OPT_NO_APPN_DESC = "Exclude application segments APPn from JPEG stream; encapsulate JPEG stream verbatim by default.";
    private static final String OPT_HELP_DESC = "Print this message";
    private static final String OPT_VERSION_DESC = "Print the version information and exit";
    private static int FF = 255;
    private static int SOF = 192;
    private static int DHT = 196;
    private static int DAC = 204;
    private static int SOI = 216;
    private static int SOS = 218;
    private static int APP = 224;
    private String charset = "ISO_IR 100";
    private String transferSyntax = "1.2.840.10008.1.2.4.50";
    private byte[] buffer = new byte[8192];
    private int jpgHeaderLen;
    private int jpgLen;
    private boolean noAPPn = false;
    private Properties cfg = new Properties();

    public Jpg2Dcm() {
        try {
            this.cfg.load(getClass().getResourceAsStream("/jpg2dcm.cfg"));
        } catch (Exception var2) {
            throw new RuntimeException(var2);
        }
    }

    public void convert(File jpgFile, File dcmFile, Properties properties) throws IOException {
        this.cfg.putAll(properties);
        this.jpgHeaderLen = 0;
        this.jpgLen = (int)jpgFile.length();
        DataInputStream jpgInput = new DataInputStream(new BufferedInputStream(new FileInputStream(jpgFile)));

        try {
            DicomObject dicomObject = new BasicDicomObject();
            dicomObject.putString(524293, VR.CS, this.charset);
            Enumeration now = this.cfg.propertyNames();

            while(now.hasMoreElements()) {
                String fos = (String)now.nextElement();
                int[] bos = Tag.toTagPath(fos);
                int dos = bos.length - 1;
                VR r = dicomObject.vrOf(bos[dos]);
                if(r == VR.SQ) {
                    dicomObject.putSequence(bos);
                } else {
                    dicomObject.putString(bos, r, this.cfg.getProperty(fos));
                }
            }

            if(this.noAPPn || this.missingRowsColumnsSamplesPMI(dicomObject)) {
                this.readHeader(dicomObject, jpgInput);
            }

            this.ensureUS(dicomObject, 2621696, 8);
            this.ensureUS(dicomObject, 2621697, dicomObject.getInt(2621696));
            this.ensureUS(dicomObject, 2621698, dicomObject.getInt(2621697) - 1);
            this.ensureUS(dicomObject, 2621699, 0);
            this.ensureUID(dicomObject, 2097165);
            this.ensureUID(dicomObject, 2097166);
            this.ensureUID(dicomObject, 524312);
            Date now1 = new Date();
            now1.setTime(1489763214);
            dicomObject.putDate(524306, VR.DA, now1);
            dicomObject.putDate(524307, VR.TM, now1);

            dicomObject.initFileMetaInformation(this.transferSyntax);
            FileOutputStream fos1 = new FileOutputStream(dcmFile);
            BufferedOutputStream bos1 = new BufferedOutputStream(fos1);
            DicomOutputStream dos1 = new DicomOutputStream(bos1);

            try {
                dos1.writeDicomFile(dicomObject);
                dos1.writeFileMetaInformation(dicomObject);
                dos1.writeHeader(2145386512, VR.OB, -1);
                dos1.writeHeader(-73728, (VR)null, 0);
                dos1.writeHeader(-73728, (VR)null, this.jpgLen + 1 & -2);
                dos1.write(this.buffer, 0, this.jpgHeaderLen);

                int r1;
                while((r1 = jpgInput.read(this.buffer)) > 0) {
                    dos1.write(this.buffer, 0, r1);
                }

                if((this.jpgLen & 1) != 0) {
                    dos1.write(0);
                }

                dos1.writeHeader(-73507, (VR)null, 0);
            } finally {
                dos1.close();
            }
        } finally {
            jpgInput.close();
        }

    }

    private boolean missingRowsColumnsSamplesPMI(DicomObject attrs) {
        return !attrs.containsValue(2621456) || !attrs.containsValue(2621457) || !attrs.containsValue(2621442) || !attrs.containsValue(2621444);
    }

    private void readHeader(DicomObject attrs, DataInputStream jpgInput) throws IOException {
        if(jpgInput.read() == FF && jpgInput.read() == SOI && jpgInput.read() == FF) {
            int marker = jpgInput.read();
            boolean seenSOF = false;
            this.buffer[0] = (byte)FF;
            this.buffer[1] = (byte)SOI;
            this.buffer[2] = (byte)FF;
            this.buffer[3] = (byte)marker;

            for(this.jpgHeaderLen = 4; marker != SOS; this.buffer[this.jpgHeaderLen++] = (byte)marker) {
                int segmLen = jpgInput.readUnsignedShort();
                if(this.buffer.length < this.jpgHeaderLen + segmLen + 2) {
                    this.growBuffer(this.jpgHeaderLen + segmLen + 2);
                }

                this.buffer[this.jpgHeaderLen++] = (byte)(segmLen >>> 8);
                this.buffer[this.jpgHeaderLen++] = (byte)segmLen;
                jpgInput.readFully(this.buffer, this.jpgHeaderLen, segmLen - 2);
                if((marker & 240) == SOF && marker != DHT && marker != DAC) {
                    seenSOF = true;
                    int p = this.buffer[this.jpgHeaderLen] & 255;
                    int y = (this.buffer[this.jpgHeaderLen + 1] & 255) << 8 | this.buffer[this.jpgHeaderLen + 2] & 255;
                    int x = (this.buffer[this.jpgHeaderLen + 3] & 255) << 8 | this.buffer[this.jpgHeaderLen + 4] & 255;
                    int nf = this.buffer[this.jpgHeaderLen + 5] & 255;
                    attrs.putInt(2621442, VR.US, nf);
                    if(nf == 3) {
                        attrs.putString(2621444, VR.CS, "YBR_FULL_422");
                        attrs.putInt(2621446, VR.US, 0);
                    } else {
                        attrs.putString(2621444, VR.CS, "MONOCHROME2");
                    }

                    attrs.putInt(2621456, VR.US, y);
                    attrs.putInt(2621457, VR.US, x);
                    attrs.putInt(2621696, VR.US, p > 8?16:8);
                    attrs.putInt(2621697, VR.US, p);
                    attrs.putInt(2621698, VR.US, p - 1);
                    attrs.putInt(2621699, VR.US, 0);
                }

                if(this.noAPPn & (marker & 240) == APP) {
                    this.jpgLen -= segmLen + 2;
                    this.jpgHeaderLen -= 4;
                } else {
                    this.jpgHeaderLen += segmLen - 2;
                }

                if(jpgInput.read() != FF) {
                    throw new IOException("Missing SOS segment in JPEG stream");
                }

                marker = jpgInput.read();
                this.buffer[this.jpgHeaderLen++] = (byte)FF;
            }

            if(!seenSOF) {
                throw new IOException("Missing SOF segment in JPEG stream");
            }
        } else {
            throw new IOException("JPEG stream does not start with FF D8 FF");
        }
    }

    private void growBuffer(int minSize) {
        int newSize;
        for(newSize = this.buffer.length << 1; newSize < minSize; newSize <<= 1) {
            ;
        }

        byte[] tmp = new byte[newSize];
        System.arraycopy(this.buffer, 0, tmp, 0, this.jpgHeaderLen);
        this.buffer = tmp;
    }

    private void ensureUID(DicomObject attrs, int tag) {
        if(!attrs.containsValue(tag)) {
            attrs.putString(tag, VR.UI, UIDUtils.createUID());
        }

    }

    private void ensureUS(DicomObject attrs, int tag, int val) {
        if(!attrs.containsValue(tag)) {
            attrs.putInt(tag, VR.US, val);
        }

    }
}
