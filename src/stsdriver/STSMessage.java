/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package stsdriver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author jim006
 */
public class STSMessage {
    
    
    public static final int MSG_GET_HW_VERSION=0x00000080;
    public static final int MSG_GET_SW_VERSION=0x00000090;
    public static final int MSG_GET_SERIAL_NUMBER=0x00000100;

    public static final int MSG_GET_CORRECTED_SPECTRUM=0x00101000;
    public static final int MSG_SET_INTEGRATION_TIME=0x00110010;

    public static final int MSG_GET_AVG_SCANS=0x00110510;
    public static final int MSG_SET_AVG_SCANS=0x00120010;

    public static final int MSG_GET_TEMPERATURE=0x00400001;
    public static final int MSG_GET_ALL_TEMPERATURE=0x00400002;
    
    
    static final char start_bytes = 0xc0c1;
    int protocol_version = 0x1000;
    int flags = 0;
    int errno = 0;
    long message_type = 0x00101000;
    long regarding=0x00000000;
    byte[] reserved = {0,0,0,0,0,0};
    byte checksum_type = 0;
    byte immediate_data_length = 0;
    byte[] immediate_data = new byte[16];
    long bytes_remaining;
    byte[] checksum = new byte[16];
    static final long footer = 0xc2c3c4c5;
    byte[] payload = null;
    
    byte[] buffer = new byte[2500];
    
    
    STSMessage(byte[] byteArray) {
        boolean status = parse(byteArray);
    }

    public STSMessage() {
        this(MSG_GET_CORRECTED_SPECTRUM, new byte[16],0);
    }
    
    public STSMessage(long message_type, byte[] immediate_data, int immediate_data_length) {
        this.message_type = message_type;
        
        if(immediate_data.length==this.immediate_data.length) {
            this.immediate_data = immediate_data;
            this.immediate_data_length = (byte) immediate_data_length;
        }
        
        
        byte[] test= getMsgBytes();
        
    }
    
    public STSMessage(long message_type, float value) {
        this.message_type = message_type;
        
        byte[] immValue = new byte[16];
        ByteBuffer buf = null;
        buf = ByteBuffer.allocate(16);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putFloat(value);
        
        immediate_data = buf.array();
        immediate_data_length = 4;
                
    }
    
    public STSMessage(long message_type, double value) {
        this.message_type = message_type;
        
        byte[] immValue = new byte[16];
        ByteBuffer buf = null;
        buf = ByteBuffer.allocate(16);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(value);
        
        immediate_data = buf.array();
        immediate_data_length = 8;
                
    }
    
    public STSMessage(long message_type, int value) {
        this.message_type = message_type;
        
        byte[] immValue = new byte[16];
        ByteBuffer buf = null;        
        buf = ByteBuffer.allocate(16);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        
        immediate_data = buf.array();
        immediate_data_length = 4;
    }
    
    public STSMessage(long message_type, byte value) {
        this.message_type = message_type;
        
        byte[] immValue = new byte[16];
        ByteBuffer buf = null;
        buf = ByteBuffer.allocate(16);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(value);
        
        immediate_data = buf.array();
        immediate_data_length = 1;
    }
    
    public STSMessage(long message_type) {
        this.message_type = message_type;
        
        
        byte[] test= getMsgBytes();
        
    }
    
    final byte [] getMsgBytes() {
        ByteBuffer buf = null;
        buf = ByteBuffer.allocate(64 + (payload!=null? payload.length : 0));
        buf.order(ByteOrder.LITTLE_ENDIAN);
        
        buf.putChar(start_bytes);
        buf.putChar((char) (protocol_version & 0xFFFF));
        buf.putChar((char) (flags & 0xFFFF));
        buf.putChar((char) (errno & 0xFFFF));
        buf.putInt((int) message_type); 
        buf.putInt((int) regarding); 
        buf.put(reserved);
        buf.put(checksum_type);
        buf.put(immediate_data_length);
        buf.put(immediate_data);
        if(payload!=null) bytes_remaining = 20+payload.length;
        else bytes_remaining = 20;
        buf.putInt((int) bytes_remaining);
        if(payload!=null) buf.put(payload);
        buf.put(checksum);
        buf.putInt((int) footer);
        byte[] test = buf.array();
        
        //System.out.println(byteArrayToHexString(test, buf.capacity()));
        
        return buf.array();
    }
    
    
    
    boolean parse(byte[] msgBytes) {
        ByteBuffer buf = ByteBuffer.wrap(msgBytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int payload_length = 0;
        try {
            protocol_version = (buf.getShort() & 0xffff);
            flags = (buf.getShort() & 0xffff);
            errno = (buf.getShort() & 0xffff);
            if(errno>0) {
                System.out.println("Error number " + errno);
                return false;
            }
            message_type = (buf.getInt()& 0xffffffffL);
            regarding = (buf.getInt()& 0xffffffffL);
            buf.get(reserved);
            checksum_type = buf.get();
            immediate_data_length = buf.get();
            int pos = buf.position();
            buf.get(immediate_data);
            pos = buf.position();
            bytes_remaining = (buf.getInt()& 0xffffffffL);
            if(bytes_remaining>0) {
                payload_length = (int) (bytes_remaining-20);
                payload = new byte[payload_length];
                buf.get(payload);
            }
            buf.get(checksum);

            switch((int) message_type) {
                case (MSG_GET_CORRECTED_SPECTRUM):
                    parseSpectrum(payload);
                    break;
                case (MSG_GET_TEMPERATURE):
                    System.out.println("This is temperature");
                    parseTemperatures();
                    break;
                case (MSG_GET_ALL_TEMPERATURE):
                    System.out.println("This is all temperatures");
                    parseTemperatures();
                    break;
                default:
                    System.out.println("Unknown message: 0x" + Long.toHexString(message_type));



            }
        }
        catch (Exception e) {
            System.out.println("Error parsing packet");
            e.printStackTrace();
            return false;
        }
        
        
        
        
        
        
        
        return true;
    }
    
    
    void parseSpectrum(byte[] msgBytes) {
        ByteBuffer buf = ByteBuffer.wrap(msgBytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int bands = (int) msgBytes.length/2;
        int[] dn = new int[bands];
        System.out.print("Spectrum: ");
        for(int i=0;i<bands;i++) {
            dn[i] = (buf.getShort() & 0xffff);
            System.out.print(dn[i] + ",");
        }
        System.out.println("");
    }
    
    void parseTemperatures() {
        //System.out.println("Immediate Data: " + this.byteArrayToHexString(immediate_data,16));
        ByteBuffer buf = ByteBuffer.wrap(immediate_data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        double temp1,temp2,temp3;
        temp1 = buf.getFloat();
        temp2 = buf.getFloat();
        temp3 = buf.getFloat();
        System.out.println("Temperatures: " + temp1 + "," + temp2 + "," + temp3);
    }
    
    public byte[] getTest() {
        int[] temp_buffer = new int[2500];
        byte[] byteBuffer = new byte[64];
        
        // Header
        temp_buffer[0] = 0xc1;
        temp_buffer[1] = 0xc0;
        
        //Protocol Version
        temp_buffer[2] = 0x00;
        temp_buffer[3] = 0x10;
        
        //Flags
        temp_buffer[4] = 0x00;
        temp_buffer[5] = 0x00;
        
        //Error Number
        temp_buffer[6] = 0x00;
        temp_buffer[7] = 0x00;
        
        //Message type
        temp_buffer[8] = 0x02;
        temp_buffer[9] = 0x00;
        temp_buffer[10] = 0x04;
        temp_buffer[11] = 0x00;
        
        //Message type
        temp_buffer[12] = 0x00;
        temp_buffer[13] = 0x00;
        temp_buffer[14] = 0x00;
        temp_buffer[15] = 0x00;
        
        //Reserved
        temp_buffer[16] = 0x00;
        temp_buffer[17] = 0x00;
        temp_buffer[18] = 0x00;
        temp_buffer[19] = 0x00;
        temp_buffer[20] = 0x00;
        temp_buffer[21] = 0x00;
        
        //Checksum type
        temp_buffer[22] = 0x00;
        
        //Immediate length
        temp_buffer[23] = 0x00;
        
        // Bytes 24-29 Unused
        
        //Bytes remaining
        temp_buffer[40] = 0x14;
        temp_buffer[41] = 0x00;
        temp_buffer[42] = 0x00;
        temp_buffer[43] = 0x00;
        
        //Bytes 44-59 Checksum
        
        //Footer
        temp_buffer[60] = 0xc5;
        temp_buffer[61] = 0xc4;
        temp_buffer[62] = 0xc3;
        temp_buffer[63] = 0xc2;
        
        
        for (int i=0;i<byteBuffer.length;i++) {
            byteBuffer[i] = (byte) (temp_buffer[i]&0xFF);
        }
        
        return byteBuffer;
        
        
    }
    
    public static String byteArrayToHexString(byte[] b, int len) {
            StringBuffer sb = new StringBuffer(len * 3);
            for (int i = 0; i < len; i++) {
              int v = b[i] & 0xff;
              if (v < 16) {
                sb.append('0');
              }
              sb.append(Integer.toHexString(v));
              sb.append(" ");
            }
            return sb.toString().toUpperCase();
    }
    
    
}
