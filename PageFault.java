package OS_ComplexClock;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PageFault implements Runnable
{
    static int pageFaultSignal = 0;                                 //pageFaultSignal is a signal to check if the page fault has occurred
                                                                    //  pageFaultSignal is 1 signifies that the page fault has occured
                                                                    //  pageFaultSignal is 0 signifies that page fault has been handled
                                                                    //                          or no page fault.
    public static Semaphore pageReady = new Semaphore(1);
    static String VirtualAddress = "";                              //keeps the virtual address that caused the page fault
    
    static int process;                                             //stores the process that caused the page fault
    static int pageNum;                                             //stores the page num that caused the page fault
    
    static Frame[] frameTable;                                      //stores the frames of the real memory.
    
    private static int frameptr = 0;                                //points to the next frame used for replacing
                                                                    //      page replacement algorithm followed clock replacement algo(Complex version)
    public static void main(String[] args)
    {
        Thread t = new Thread( new PageFault() );
        t.start();
    }

    private static void writeFrameToHDD(int frameNum , int pid , int pageNum) throws InterruptedException
    {
        //Simulating the writing of page to hard disk
        //      takes a little bit more time.
        System.out.println("Frame " + frameNum + " containing pageNum " + pageNum + " of " + pid +  " is being written into the hard disk.....");
        Thread.sleep( 2020 );
    }

    private static void LoadPage(Frame frame , int pid , int pageNum) throws InterruptedException
    {
        //Simulate the reading of page into the hard disk
        //      takes exactly the same time as writing into the hard disk
        System.out.println("Loading into frame " + frame.frameNum + " page " + pageNum + " of process " + pid );
        frame.use = true;
        Thread.sleep( 2020 );
    }

    private static void modifyNewProcessPTE(Object get, int frameNum , String VirtualAddress)
    {
        //pageTable contains the PTE table of the page fault process
        //Get the PTE of the page fault process
        //  put present bit as 1
        //  modified bit as 0
        //update that value in the corresponding PTE table
        Hashtable pageTable = (Hashtable) get;
        String newValue = "10" + getStringFrame(frameNum);
        //updation
        int pageNum = HardWare.getPageNum( VirtualAddress );
        ((Hashtable)Main.outerPTEtable.get( Main.process )).put( pageNum , newValue );

    }

    private static String getStringFrame(int frameNum)
    {
        //returns the frame number in binary with 14 bits
        //                      rest 14 bits of the PTE contains the frame num
        //ex : frame num is 5 : returns 00000000000101
        String stringFrame = "";
        
        while( frameNum > 0 )
        {
            stringFrame = frameNum%2 + "" + stringFrame;
            frameNum = frameNum/2;
        }

        int length = stringFrame.toCharArray().length;

        while( length < 14)
        {
            stringFrame = "0" + stringFrame;
            length++;
        }
        return stringFrame;
    }

    private static void modifyOldPTE(Object pt , int pageNum)
    {
        //The PTE of the page written to HDD is initialized to
        //  no frame is contains the page
        //  The page is not present in HDD
        //  The page is not modified
        Hashtable pageTable = (Hashtable)pt;
        String changedPTE = "0000000000000000";
        pageTable.put( pageNum , changedPTE);
    }

    private static String getStringFromArray(char[] charArray)
    {
        //converts the char array to string
            //  returns the string
        String temp = "";
        for(int i=0 ; i<charArray.length ; ++i)
        {
            temp += charArray[i];
        }
        return temp;
    }

    private static Frame step1()
    {
        int current = frameptr;
        if( !frameTable[current].use && !frameTable[current].modified )
        {
            frameptr = (frameptr + 1 ) % frameTable.length;
            return frameTable[current];
        }
        for(int i = (current+1) % frameTable.length ; i != current ; i = (i + 1)%frameTable.length )
        {
            if( !frameTable[i].use && !frameTable[i].modified)
            {
                frameptr = (i + 1) % frameTable.length;
                return frameTable[i];
            }
        }
        return null;
    }

    private static Frame step2()
    {
        int current = frameptr;
        if( !frameTable[current].use )
        {
            frameptr = (frameptr + 1) % frameTable.length;
            return frameTable[current];
        }
        else
        {
            frameTable[current].use = false;
        }
        
        for(int i = (current + 1)%frameTable.length ; i != current ; i = (i + 1)%frameTable.length )
        {
            if( !frameTable[i].use)
            {
                frameptr = (i + 1)% frameTable.length;
            }
            frameTable[i].use = false;
        }
        
        return null;
    }
    private static Frame getNextFrame()
    {
        //The clock replacement(Complex version) algorithm is followed to get the next frame for replacement
        //  step 1 : get a frame with u = 0 m = 0
        //  step 2 : get a frame with u = 0 m = 1
        //                 during traversal if u = 1 change u = 0
        //                  else return the frame with u = 0
        //  if no frame is obtained then repeat step 1
        //  if still frame is not found repeat step 2.

        //step 1
        Frame frame = step1();
   
        //step 2
        if( frame == null ) frame = step2();

        if( frame == null )  frame = step1();

        if( frame == null ) frame = step2();
        
        return frame;
    }

    public void run()
    {
        //Get the number of frames
        //  initialize the frame table;
        //while the schedular is running
        //  wait for page fault signal
        //  if signaled handle the page fault
        
        int numOfFrames = Main.nFrames;
        frameTable = new Frame[ numOfFrames ];
        for(int i=0 ; i<frameTable.length ; ++i)
        {
            frameTable[i] = new Frame(i);
        }
        
        //while the scheduler is running
        while( !Main.terminate )
        {
            //If a pagefault is signaled then call the handler
            if( pageFaultSignal == 1)
            {
                try
                {
                    pageFaultHandler();
                    //After the page has been handled.
                    pageFaultSignal = 0;
                } catch (InterruptedException ex)
                {
                    Logger.getLogger(PageFault.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }  
    }
    
    private static synchronized void  pageFaultHandler() throws InterruptedException
    {
       System.out.println("Page fault has occured ");
       pageReady.acquire();
       
       //->critical section has been entered
       
       //get the frame of that has to be loaded.
       Frame frame = getNextFrame();

       //if the frame is occupied
       //   if the frame is modified
       //       write the frame to the HDD ( simulation )
       //   modify PTE of the previous process ( present bit 0)
       //Load the page to the frame
       //modify the PTE of new process
       //modify the FrameTE
       
       
       if(frame.occupied)
       {
             if( frame.modified )
                        writeFrameToHDD( frame.frameNum , frame.pid , frame.pageNum);
             modifyOldPTE( (Hashtable)Main.outerPTEtable.get( frame.pid ), frame.pageNum ); //sending the oldprocess
       }
        
       LoadPage( frame , process , pageNum );
       modifyNewProcessPTE( (Hashtable)Main.outerPTEtable.get(process) , frame.frameNum , VirtualAddress );
       //modifying the frame table entry.
       frame.modified = false;
       frame.occupied = true;
       frame.pid = process;
       frame.pageNum = pageNum;
       
       //This has to be modified
      
       //->exiting critical section
       pageReady.release();
    }
}

class Frame
{
    int pid;
    boolean occupied;
    boolean modified;
    int frameNum;
    int pageNum;
    boolean use;    //for clock replacement algorithm.

    Frame(int i)
    {
        pid = -1;
        frameNum = i;
        pageNum = -1;
        occupied = false;
        modified = false;
        use = false;
    }

    static void freeFrame( Frame frame)
    {
        //It releases the frame from the ownership of a process.
        frame.modified = false;
        frame.occupied = false;
        frame.pid = -1;
        frame.pageNum = -1;
        frame.use = false;
    }
}
