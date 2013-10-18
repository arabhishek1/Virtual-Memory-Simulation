package OS_ComplexClock;
import java.util.*;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main implements Runnable{
    static Hashtable outerTable;        //process table containing hashtable of accesses per process.
    static Hashtable outerPTEtable;     //process table containing hashtable of pagetable
    static ArrayList<Integer> pidArray; //pid Array list containing the pids
    static Hashtable accessTable;       //a hashtable containing the accesspointer of each process.

    
    static int process;                 //pid of the present process.
    static boolean terminate = false;   //to terminate all the threads after successful completion.
    static int numProcesses=0;          //total number of Processes in the pid Array
    static int nFrames;                 //number of frames in the real memory.
    

    static Semaphore running = new Semaphore(1);    //to protect scheduling during translation.

    public static void init(){

          //initializes all the hashtables, pidArray.

          outerTable = new Hashtable(50);               //hashtable of max 50 capacity to hold the inputs.
          outerPTEtable = new Hashtable(50);            //hashtable of max 50 capacity to hold the 50 processes at the max.

          getInput(outerTable);                         //filling accesses from the input file.

          pidArray = new ArrayList<Integer>();          // store the pids in an arrayList

          storePid(pidArray, outerTable);                //put the pids from outerTable into pidArray.
          accessTable = new Hashtable(numProcesses);      //initializes the accesstable with num of processes.

          init_accessTable();                             //putting initial pointer values of all the processes.

          process = (int)pidArray.get(0);                  //initiating the running process(first)

          createPTEtable(pidArray, outerPTEtable);          // creating the PTE table for each process in the pidArray
                  
    }

    private static void init_accessTable()
    {
        //initializes the access table with initial access pointers of each process.
        for(int i=0;i<numProcesses;++i)
        {
            accessTable.put(pidArray.get(i), 1);            
        }
    }

    public static void main(String[] args) 
    {
        try {
            //initialize
            //get real memory size
            //creation and start of
            // 1. pagefault thread
            // 2. hardware translation thread
            // 3. scheduling thread
            init();
            System.out.printf("Enter the size of memory in KB< 4 - 16 > : ");
            Scanner in = new Scanner(System.in);
            nFrames = in.nextInt();

            Main schedule = new Main();
            Thread main_thread = new Thread(schedule);
            main_thread.start();
            Thread.sleep(100);

            Thread pfThread = new Thread(new PageFault());
            pfThread.start();
            Thread.sleep(100);

            Thread hwThread = new Thread(new HardWare());
            hwThread.start();
            
            
        } catch (InterruptedException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static int getIndex(int pid)
    {
        //getting the index of the pidArray of corresponding pid.
        for(int i=0;i<pidArray.size(); ++i)
        {
            if(pidArray.get(i) == pid)
            {
                return i;
            }
        }
        SOP("No process with the Index");
        return 0;
    }

    static void terminate(int pid, int index)
    {
        //used to terminate the process with corresponding pids.
        outerTable.remove(pid);                                 //remove the pid from the outerTable
        outerPTEtable.remove(pid);                              //remove the pid from the outerPTE table
        Integer returnI = pidArray.remove(index);               //remove the element from pidArray of given index
        
        pidArray.trimToSize();                                  //updating the pidArray
        numProcesses--;                                         //after removing the pid from pidArray, numProcesses is reduced by one.

        //realease the frames owned by the removed process.
        for(int m = 0 ; m < Main.nFrames ; ++m){
            if( PageFault.frameTable[m].pid == pid)             //if this process holds this frame, free the frame
            {
                Frame.freeFrame( PageFault.frameTable[m]);
            }
        }
        System.out.printf("The process removed is : "+ returnI + " => pidArray[");               //displaying the removed process.
        for(int k=0; k< pidArray.size(); ++k)   System.out.printf("%4d", pidArray.get(k));
        System.out.printf("   ]\n");
             
    }

     static void createPTEtable(ArrayList<Integer> pidArray, Hashtable outerPTEtable)
     {
         //creating PTE table for each process in pidArray.
          for(int k=0;  k<pidArray.size();  ++k)
          {
             Hashtable innerPTEtable = new Hashtable(64);

             for(int j=0;    j<64; ++j )
                    innerPTEtable.put(j,"0000000000000000" );               //making the innerPTE entries defualt = "000000000000"

             outerPTEtable.put( pidArray.get(k), innerPTEtable);            //putting the innerPTE table reference into outer PTE table
          }

     }


    static void storePid(ArrayList pidArray, Hashtable outerTable)
    {
        //get the set of keys from the outertable and assign those pids to the arrayList

        Set s;
        s = outerTable.keySet();                //getting the all keys in a set.
                    
        Object[] temp;
        temp=  s.toArray();

        numProcesses = temp.length;
        
        for(int i=temp.length-1  ;  i>=0  ; --i)
        {
            pidArray.add(temp[i]);             //adding keys to the pidArray
        }

        //printing the number of process and the pid array.
        System.out.printf("Number of Processes : %d => [",numProcesses);
        for(int k=0; k< pidArray.size(); ++k)   System.out.printf("%4d", pidArray.get(k));
        System.out.printf("   ]\n");
        pidArray.trimToSize();
    }



    static String getFilename()
    {
            //get the filename of input file
            SOP("Enter the input fileName");
            Scanner sin = new Scanner(System.in);
            String in = sin.nextLine();
            String path = "C:\\Users\\Abdus Salam\\Documents\\NetBeansProjects\\ostest\\src\\ostest\\" + in;
            return path;
    }

    static void SOP(Object s)
    {
        System.out.println(s);
    }

    static void getInput(Hashtable outerTable)
    {
            //getting the input from the file and populating the outerTable
            String fname = getFilename();
            String[] lines = returnLine(fname);    //getting all the lines of the input file

            int i=0;            //for keeping the line updated
            int[] innerKey =  new int[50];   //for innerkey of innertable
            for(int m=0; m<50; ++m) innerKey[m] = 1;
            
            while(lines[i]!=null)
            {
                    StringTokenizer st = new StringTokenizer(lines[i],", ");         //dividing into tokens
                    String pidString = st.nextToken();                               //getting the pid of the input line
                    String value = st.nextToken().toUpperCase() + st.nextToken().toUpperCase();                  //concatenating the mode of access and virtual address; ex: RF123
                    
                    int pid = Integer.parseInt( pidString );
                    Hashtable h = (Hashtable)outerTable.get( pid );
                    if(h == null)
                    {
                        outerTable.put(pid, new Hashtable(50));                      //create a hashtable for every new process.
                    }
                    ((Hashtable)outerTable.get(pid)).put(innerKey[pid], value);     //put the above hashtable reference to the respective pid

                    innerKey[pid]++;                                                //counter for innerTable
                     i++;                                                           //counter for next line
            }
    }

    static String[] returnLine(String fname)
    {
            //It returns the line by line from the file.
          try{
                // Open the file.
                String[] array = new String[50];
                int k=0;
                FileInputStream fstream = new FileInputStream(fname);
                // Get the object of DataInputStream
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String strLine;

                //Read File Line By Line
                while ((strLine = br.readLine()) != null)
                {
                      array[k] = strLine;
                      k++;
                }

                return array;
          
            }catch (Exception e)
            {
                //Catch exception if any
                System.err.println("Error: " + e.getMessage());
            }
        return null;
    }

    static String getRaw() 
    {
        //It returns the raw address from the hashtable         ex: RF123
        Hashtable reference = (Hashtable) outerTable.get(process);
        String rawAddr = "";
       
        if( reference != null)
        {
            int accessNo = (Integer)accessTable.get(process);
            if(reference.containsKey(accessNo))
            {
                rawAddr =  (String) reference.get(accessNo);
                ((Hashtable)outerTable.get(process)).remove(accessNo);
            }
            else
            {
                terminate(process, getIndex(process));              //if there is no access, terminate that process.
            }

            accessTable.put(process, accessNo+1);                   //putting the access pointer into the access table.
        }

        return rawAddr;
    }

    public void run()
    {
         schedule();
    }


    private static int index = -1;               //used for scheduling
    
    private void schedule()
    {
        //used for scheduling the processes.
        while(!terminate)                       //schedule till all the processes complete
        {

            try
            {
                

                running.acquire();                  //running semaphore is acquired,since when hardware is translating,
                                                    //process should not be scheduled.
                PageFault.pageReady.acquire();      //pageReady semaphore is acquired, since when page fault is handled,
                                                    //the process must not be scheduled.
                
                // ->critical section begins..
                
                if(numProcesses!= 0)
                {
                      index = (index + 1) % numProcesses;
                      process = pidArray.get(index);
                }
                else
                {
                    HardWare.printRealMemory();
                      SOP("All the processes are completed successfully!!!!!!!" + "\nEx(c)iting...");
                      terminate = true;
                }
                
                //->critical section ends..
                PageFault.pageReady.release();      //releasing the pageReady semaphore.
                running.release();                  //releasing the running semaphore.
                if(!(terminate))
                {
                     SOP("\n\n-----------------------------------------------------------------------------------------------------------");
                    SOP("The running process... " + process);
                }    
                Thread.sleep(2000);


            } catch (InterruptedException ex)
            {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }
}
