package OS_ComplexClock;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HardWare implements Runnable
{
    public static void main(String[] args)
    {
         //used for unit testing....
        HardWare h = new HardWare();
        Thread t = new Thread( h );
        t.start();
    }

    public void run()
    {
        while( !Main.terminate)
        {
            //get the raw address and if not null, translate the virtual address.
            String rawVirtualAddress = Main.getRaw();

            if( !( ( rawVirtualAddress == null) || rawVirtualAddress.equals("") ) )             //if raw address is not null
            {
                    try
                    {
                        Main.running.acquire();                  //acquiring the running semaphore, to prevent scheduling while translation.
                       
                        //->critical section starts...
                        
                        String VirtualAddress = getVirtualAddress(rawVirtualAddress); //ex: get F123 from RF123
                        VirtualAddress = getInBinary(VirtualAddress);                 //ex: get 1111000100100011 from F123

                        printRealMemory();
                        System.out.println( "Access : " + rawVirtualAddress );

                        String physical = translateToPhysical(VirtualAddress);        //changes the page number to frame number.

                        use(rawVirtualAddress);                                       //ex: uses the access RF123
                                                                                      //reads the word from translated physical address of F123.
                        
                        //-> critical section ends...

                        Main.running.release();                                       //releasing running semaphore.

                    } catch (InterruptedException ex)
                    {
                        Logger.getLogger(HardWare.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    
                try
                {
                    Thread.sleep(420);                  //why sleep?
                                                        //two threads hardware and schedule start simultaneously
                                                        //acquire function is not synchronized according to our observation.
                                                        //so both can acquire that.
                                                        //when both acquire, before actual scheduling, hardware thread gains an access of previous process!
                                                        //then schedule is made..this leads to process access tuple, leading to an error....
                                                        //so we sleep hardware thread after its access completes thereby allowing scheduling to happen first.
                                                        //this will lead to correction of this error.(blunder).
                } catch (InterruptedException ex)
                {
                    Logger.getLogger(HardWare.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private String getInBinary(String VirtualAddress)
    {
        //returns the binary equivalent of the hex address
        char[] charray = VirtualAddress.toCharArray();
        VirtualAddress = "";
        
        for( int i = 0 ; i < charray.length ; ++i)
        {
            VirtualAddress += getBin( charray[i] );
        }
        return VirtualAddress;
    }
       
    private String getBin( char ch )
    {
            switch( ch )
            {
                case '0' : return "0000";
                case '1' : return "0001";
                case '2' : return "0010";
                case '3' : return "0011";
                case '4' : return "0100";
                case '5' : return "0101";
                case '6' : return "0110";
                case '7' : return "0111";
                case '8' : return "1000";
                case '9' : return "1001";
                case 'A' : return "1010";
                case 'B' : return "1011";
                case 'C' : return "1100";
                case 'D' : return "1101";
                case 'E' : return "1110";
                case 'F' : return "1111";
            }
            System.out.println("Invalid virtual address :");
            System.exit(0);
            return "";
    }

    
    private String translateToPhysical(String VirtualAddress) 
    {
        //translation of virtual to physical address.
            //if page not present, raise pagefault.
            //Then translate virtual address to physical address
        int pageNum = getPageNum(VirtualAddress);           //to know which page has caused the page fault.
        String physicalAddress = "";                        //To hold the translated physical address
        
        try 
        {
            PageFault.pageReady.acquire();      //acquiring the pageReady semaphore.
            
            //-> critial sections starts...
            
            if (isFault(pageNum))
            {
                //if page fault occurs
                    //Signal the page fault
                PageFault.pageFaultSignal = 1;
                PageFault.process = Main.process;
                PageFault.pageNum = pageNum;
                PageFault.VirtualAddress = VirtualAddress;
            }
            
            //->critical section ends...
            PageFault.pageReady.release();

            try
            {
                Thread.sleep(1000);                                      //sending pagefault handler thread to run first.

            } catch (InterruptedException ex)
            {
                Logger.getLogger(HardWare.class.getName()).log(Level.SEVERE, null, ex);
            }

            PageFault.pageReady.acquire();                  //this is used to wait till the page is ready before translation.
            PageFault.pageReady.release();                  //releasing pageReady semaphore.

            //actually translate.
                //get corressponding PTE of the page num
                //modify the page num of virtual address to frame number ==> we get the physical address
                //return the physical address
            String PTE = (String) ( (Hashtable )Main.outerPTEtable.get( Main.process )).get( pageNum);   //Get the Page Table entry that contains the
                                                                                                            //contains the frame num of the word to be
                                                                                                            //accessed.
            //modifying the virtual address : convert section of pageNum -> frameNum in the address
            char[] PTEcharArray = PTE.toCharArray();
            char[] VirtAddcharArray = VirtualAddress.toCharArray();
            for (int i = 0; i < 6; ++i)
            {
                VirtAddcharArray[i] = PTEcharArray[10 + i];
            }
            physicalAddress = getStringFromArray(VirtAddcharArray);          //converts char array to string and returns the string.

        } catch (InterruptedException ex)
        {
            Logger.getLogger(HardWare.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return physicalAddress;                                                 //return the physical address.
    }

    
        private static String getStringFromArray(char[] charArray)
        {
            //converts the char array to string
            //  returns the string
            String temp = "";

            for(int i=0 ; i<charArray.length ; ++i){
                temp += charArray[i];
            }
            return temp;
        }

        
    public static int getPageNum(String VirtualAddress)
    {
        //get page number from the virtual address.
        char[] array = VirtualAddress.toCharArray();
        int num = 0;
        for(int i = 0 ; i < 6  ; ++i)
        {
            num += ( (array[i] == '0')? 0 : 1 ) * power( 2 , 5 - i);
        }
        return num;
    }

    private static  int power(int x, int y )
    {
        //returns x^y
        if( y == 0) return 1;
        if( y==1 ) return x;
        return  x * power( x , y-1);
    }

    private boolean isFault(int pageNum)
    {
        //returns true if the page is absent in the main memory
        String str = (String) ((Hashtable)Main.outerPTEtable.get( Main.process )).get( pageNum );
        char[] array = str.toCharArray();
        
        return array[0] == '0';         //checks if the present bit is 0 ==> page not present in the main memory
    }

    private String getVirtualAddress(String rawVirtualAddress)
    {
        // ex : returns F123 from the raw address RF123
        char[] array = rawVirtualAddress.toCharArray();
        char[] temp = new char[4];
        
        for(int i=0 ; i < temp.length ; ++i)
        {
            temp[i] = array[i+1];
        }
        return getStringFromArray( temp );
    }

    private void use(String rawVirtualAddress)
    {
        //simulates the use of a word.
        //ex : reads physical address( F123 ) from the main memory for RF123 access

        char[] array = rawVirtualAddress.toCharArray();
        String VirtualAddress = getVirtualAddress( rawVirtualAddress );
        VirtualAddress = getInBinary(VirtualAddress);

        if(array[0] == 'W')
        {
            //if the access mode is write i.e we are writing to the page in the real memory
                //modify the page table entry and the frame table entry to signify modification

            //modifying the Page table entry of the page to be modified
                //get PTE of the corresponding page
                //modifying the modified bit to 1
                //storing the modified PTE to outerPTEtable
           int n =  getPageNum( VirtualAddress );

           String PTE = (String)((Hashtable)Main.outerPTEtable.get(Main.process)).get( n );
           char[] PTEcharArray = PTE.toCharArray();
           PTEcharArray[1] = '1';

           PTE = getStringFromArray(PTEcharArray); 
           ((Hashtable)Main.outerPTEtable.get(Main.process)).put( n , PTE );

           //modifying the frame table entry of the modified frame.
           Frame frame = getFrameFromAddress( VirtualAddress);
           frame.modified = true;
        }

        Frame frame = getFrameFromAddress(VirtualAddress);
        frame.use = true;

        //simulating the usage time of the accessed word
        try 
        {
            Thread.sleep(500);
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(HardWare.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Frame getFrameFromAddress( String VirtualAddress)
    {
        //returns the frame table entry of the corresponding
            //get corresponding PTE
            //translate the virtual address to physical address
            //get the frame number from the translated physical address
        int n =  getPageNum( VirtualAddress );
        String PTE = (String)((Hashtable)Main.outerPTEtable.get(Main.process)).get( n );    //gets the PTE of the corresponding page

        //translating the virtual address to physical address
        char[] PTEcharArray = PTE.toCharArray();
        char[] VirtAddcharArray = VirtualAddress.toCharArray();
        for(int i=0 ; i < 6 ; ++i)
        {
             VirtAddcharArray[i] = PTEcharArray[10 + i];
        }
        String physicalAddress = getStringFromArray( VirtAddcharArray );

        //returns the frame number from tranlated physical address
        int frameNum = getFrameNum( physicalAddress );
        return PageFault.frameTable[frameNum];
        }

    private int getFrameNum(String physicalAddress)
    {
        //functionality of getPageNum :
            //if applied to : Virtual address( page num , offset ) returns page num
            // ====> if applied to  : physical address( frame num , offset ) returns frame num
        return getPageNum(physicalAddress);
    }

    public static void printRealMemory() {
        //printing the frames in the real memory
        System.out.printf("Real memory : ");
        for(int i = 0 ; i < Main.nFrames ; ++i)
        {
            //print each frames attributes
            System.out.printf( PageFault.frameTable[i].pid + "." +PageFault.frameTable[i].pageNum);
            System.out.printf( "." + ((PageFault.frameTable[i].use)? "1" : "0") + "." + ((PageFault.frameTable[i].modified)? "1" : "0") + " | ");
        }
        System.out.println();
    }

}