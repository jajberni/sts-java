/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package stsdriver;

import gnu.io.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Enumeration;

/**
 *
 * @author jim006
 */
public class STSDriver {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        STSDriver sts = new STSDriver("COM1");
        //sts.sendTest();
    }
    
    STSDriver (String comPort) {
        serialPortID = comPort;
        initSerialPorts();
    }
    
    private void initSerialPorts() {
        //
        // Get an enumeration of all ports known to JavaComm
        //
        Enumeration<CommPortIdentifier> portIdentifiers = CommPortIdentifier.getPortIdentifiers();
        String portName="";
        
        System.out.println("Available Serial Ports:");
        
        while (portIdentifiers.hasMoreElements())
        {
            CommPortIdentifier pid = (CommPortIdentifier) portIdentifiers.nextElement();

            if(pid.getPortType() == CommPortIdentifier.PORT_SERIAL)
            {
                portName=pid.getName();
                if(portName.equals(serialPortID)) {
                    portIdentifier = pid;
                    System.out.println(portName + " -> Attempt to connect!");
                    boolean status = connectPort();
                    if(status) System.out.println("Connected!");
                    else {
                        System.out.println("Error... exiting");
                        System.exit(1);
                    }
                    return;
                }
                else System.out.println(portName);
                
            }            
        }       

    }
    
    public void sendTest() {
        STSMessage msg = new STSMessage();
        byte[] byteMsg = msg.getTest();
        System.out.println("Sending test...");
        serialWriter.write(byteMsg, 0, byteMsg.length);
               
        
        
    }
    
    public void sendMessage(STSMessage msg) {
        byte[] byteMsg = msg.getMsgBytes();
        System.out.println("Sending packet...");
        serialWriter.write(byteMsg, 0, byteMsg.length);
    }
    
    
    private boolean connectPort() {
        serialPort = null;
        try {
            serialPort = (SerialPort) portIdentifier.open(
                "STS", // Name of the application asking for the port 
                10000   // Wait max. 10 sec. to acquire port
            );
            System.out.println("Connected to: " + serialPort.toString());
        } catch(PortInUseException e) {
            System.err.println("Port already in use: " + e);
            return false;
        }
        //
        // Now we are granted exclusive access to the particular serial
        // port. We can configure it and obtain input and output streams.
        try {
        serialPort.setSerialPortParams(
            9600,
            SerialPort.DATABITS_8,
            SerialPort.STOPBITS_1,
            SerialPort.PARITY_NONE);
        
        }
        catch(Exception e) {
            System.out.println("Error configuring port...");
            e.printStackTrace();
            return false;
        }
        //
        // Open the input Reader and output stream. The choice of a
        // Reader and Stream are arbitrary and need to be adapted to
        // the actual application. Typically one would use Streams in
        // both directions, since they allow for binary data transfer,
        // not only character data transfer.
        //
        serialReader = null;
        serialWriter = null;

        try {
            InputStream in1 = serialPort.getInputStream();
            (new Thread(new SerialReader(in1))).start();
          //serialReader = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
          serialWriter = new PrintStream(serialPort.getOutputStream(), true);
          
        } catch (IOException e) {
          System.err.println("Can't open input stream: write-only");
          serialReader = null;
          return false;
        }
        
        return true;
        
    }
    
    public void setIntegrationTime(int integrationTime) {
        this.integrationTime = integrationTime;
        STSMessage msg = new STSMessage(STSMessage.MSG_SET_INTEGRATION_TIME, integrationTime);
        this.sendMessage(msg);
    }
    
    public void setScanAverage(int avgScans) {
        this.scanAverage = avgScans;
        STSMessage msg = new STSMessage(STSMessage.MSG_SET_AVG_SCANS, avgScans);
        this.sendMessage(msg);
    }
    
    
    private BufferedReader serialReader;
    private PrintStream serialWriter;
    private String serialPortID="COM1";
    private CommPortIdentifier portIdentifier = null;
    private CommPort commPort;
    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    
    private boolean connected=false;
    
    private double integrationTime; //Integration time in us
    private int scanAverage; //Scans to average
    private int wlCoeffCount;
    private double[] wlCoefficients;
    
    
    
    
    public static class SerialReader implements Runnable 
    {
        InputStream in;
        int pos=0;
        byte[] packetBuffer = new byte[3000];
        int packetLength=0;
        boolean potentialHeader = false;
        boolean potentialFooter = false;
        int footerCount=0;
        boolean gotHeader = false;
        boolean gotFooter = false;
        STSMessage lastMessage = null;
        //OutputStream out;
        
        public SerialReader ( InputStream in )
        {
            this.in = in;
            //this.out = out;
        }
        
        public void run ()
        {
            byte[] buffer = new byte[1024];
            int len = -1;
            int b1,b2;
            int headerStart=0;
            try
            {
                while ( ( len = this.in.read(buffer)) > -1 )
                {
                    if(len>0) {
                        for(int i=0;i<len;i++) {
                            b1=buffer[i]&0xFF;
                            switch (b1) {
                                case 0xc1:
                                    potentialHeader=true;
                                    break;
                                case 0xc0:
                                    if(potentialHeader==true) {
                                        gotHeader=true;
                                        pos=0;
                                        headerStart=i;
                                        System.out.println("Got Header");
                                    }
                                    break;
                                case 0xc5:
                                    potentialFooter=true;
                                    gotFooter=false;
                                    footerCount++;
                                    break;
                                case 0xc4:
                                    if(potentialFooter==true && footerCount==1)  footerCount++;
                                    else {
                                        potentialFooter = false;
                                        gotFooter=false;
                                        footerCount = 0;
                                    }
                                    break;
                                case 0xc3:
                                    if(potentialFooter==true && footerCount==2)  footerCount++;
                                    else {
                                        potentialFooter = false;
                                        gotFooter=false;
                                        footerCount = 0;
                                    }
                                    break;
                                case 0xc2:
                                    if(potentialFooter==true && footerCount==3)  {
                                        gotFooter=true;
                                        potentialFooter=false;                                        
                                        footerCount=0;
                                        System.out.println("Got Footer!: " + pos);
                                        lastMessage = new STSMessage(packetBuffer);
                                        // Parse packet and notify
                                        
                                    }
                                    else {
                                        potentialFooter = false;
                                        gotFooter=false;
                                        footerCount = 0;
                                        
                                    }
                                    break;
                                default:
                                    if(gotHeader) {
                                        packetBuffer[pos] = buffer[i];
                                        pos++;
                                    }
                            }
                        }
                    }
                    
                    
                    //System.out.print(new String(buffer,0,len));
                    if(len>0) {
                        //System.out.println(byteArrayToHexString(buffer, len));
                        
                    }
                    
                                       
                          
                    
                    //out.write(buffer, 0, len);
                }
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }            
        }
        
        public static String byteArrayToHexString(byte[] b, int len) {
            StringBuffer sb = new StringBuffer(len * 2);
            for (int i = 0; i < len; i++) {
              int v = b[i] & 0xff;
              if (v < 16) {
                sb.append('0');
              }
              sb.append(Integer.toHexString(v));
            }
            return sb.toString().toUpperCase();
        }
    }
    
}
