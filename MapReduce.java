/* Xian Chen
 * 377 Lab2: MapReduce
 */
import java.io.*;
import java.util.*;

public class MapReduce{
	/* 'map' is a TreeMap representing the global inverted index. It contains the word as the 
	 * key and a ArrayList of fileLine objects which records the file name and line number
	 * combination the word can be found at. 'map' is filled by the ReduceThread. */
	static TreeMap<String, ArrayList<fileLine>> map = new TreeMap<String,ArrayList<fileLine>>();

	// fileLine is a class that stores a filename and line number.
	class fileLine{
		String file; //filename
		int line; //line number
		
		fileLine(String f, int l){
			file = f;
			line = l;
		}
		
		public String toString(){
			return (file+ " " +line); //returns the filename and line number
		}
	}
	
	/* This class identifies the word and where it can be found (the filename and the line
	 * in the file it can be found). The filename and line number is the fileLine object which
	 * class is constructed above. */
	class Word{
		String word;
		fileLine stats;
		
		Word(String w, fileLine s){
			word = w;
			stats = s;
		}
	}
	
	/* The Buffer class is the bounded buffer. It contains a queue of 'Words' (the above class)
	 * in which the ReduceThread is to read (pop) from and the MapThread is to push into. */
	class Buffer{
		Deque<Word> buff = new ArrayDeque<Word>(); //the queue of 'Words'
		int maxSize=10; //maximum size as stated in the lab requirements
		
		/* getData() is used by the ReduceThread to obtain the 'Word' to construct its inverted
		 * index. If the queue (buff) is empty, then the thread waits. Otherwise, the thread is
		 * notified that there something to be 'consumed' in the queue (woken up). The entry is 
		 * then popped and returned to the requesting thread. */
		synchronized Word getData() throws InterruptedException{
			if (buff.isEmpty()){ //if empty, wait
				System.out.println("*Buffer if empty! Waiting...");
				wait();
				return null;
			}
			
			System.out.println("Request: Buffer Getting Object....  Word: '"+buff.peekFirst().word +"' ; Buffer Size: "+(buff.size()-1) );
			notify();
			return buff.pop(); 
		}
		
		/* setData is used by the MapThread to pass off information to the ReduceThread. 
		 * If the queue (buff) is at its max size (10), then the MapThread has to wait until
		 * the queue size decreases before adding an entry. If the size of the queue is not at
		 * at its max size, or if the size has decreased, then the MapThread can add an entry
		 * to the queue. A waiting thread is notified (woken up) since there is now something
		 * to "consume". */
		synchronized boolean setData(String w, fileLine s) throws InterruptedException{
			if(buff.size()>=maxSize){
				this.wait();
				System.out.println("*Buffer is full! Waiting...");
			}
			System.out.println("Request: Buffer Adding '"+w+"' ; Buffer Size "+(buff.size()+1));
			buff.push(new Word(w,s));
			notify();
			return true;
		}
	
	}
	
	/* The shared counter-- only one thread can have access to the counter at any time. 
	 * Therefore two threads cannot increment or decrement the counter at the same time */
	class SharedCounter{
		private int counter;
		
		SharedCounter(int c){
			counter = c;
		}
		
		synchronized void dec(){
			counter--;
		}	
		synchronized int getCounter(){
			return counter;
		}
	}
	
	/* Reads words from a file, hashes it, and passes it off to the corresponding thread */
	class MapThread extends Thread{
		Buffer[] buff; //array of all the buffers for each ReduceThread
		String filename; //file in which the MapThread reads
		SharedCounter count; //shared data
		BufferedReader br; //use to read files
		
		MapThread(Buffer[] b, SharedCounter c, String f) throws Exception{
			buff = b;
			filename = f;
			count = c;
			
			br = new BufferedReader(new FileReader(filename)); //allow the file to be read
		}
		
		//the hashing function that hashes words to specific reduce threads
		int hash(String line){
			int hash=0;
			for (int i=0; i<line.length(); i++){
				hash = (2*hash + line.charAt(i)); 
			}
			//makes sure that the hash is not greater than the number of reduce threads
			return hash%buff.length; 
		}
		
		public void run(){
			int lineNum=0;
			String line;
			try {
				line = br.readLine(); //reads a word per line
		
				while (line!=null){ //if it is not the end of the file...
					lineNum++;
					//creates a fileLine object that stores the filename and the line number
					fileLine stats = new fileLine(filename,lineNum);
	
					int threadNum = hash(line.toLowerCase()); //hashes the word
					System.out.println("MT - Hashing '"+line.toLowerCase()+"' to RT # "+threadNum);
					/* adds the word and filename,line number combo to the buffer corresponding
					 * to the hash number */
					buff[threadNum].setData(line.toLowerCase(), stats);
					line = br.readLine(); //reads the word on the next line
				}
				br.close(); //close the buffer reader.
				count.dec(); //decrement the sharedcounter
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	// The ReduceThread consumes the next word in its buffer and constructes the inverted index
	class ReduceThread extends Thread{
		//Current thread's inverted index
		TreeMap<String, ArrayList<fileLine>> invertedIndex = new TreeMap<String,ArrayList<fileLine>>();
		Buffer reduceBuff; //The buffer in which the Reduce Thread consumes from
		SharedCounter count; //shared data (shared with MapThreads)
		int threadID;

		ReduceThread(Buffer b, SharedCounter c, int r){
			reduceBuff = b;
			count = c;
			threadID = r;
		}
		
		public void run(){
			Word current = null;
			//if the shared data is larger than 0 and if the buffer is not empty
			while(count.getCounter()>0 || !reduceBuff.buff.isEmpty()){
				if (!reduceBuff.buff.isEmpty()){ // if the buffer if not empty
					try {
						current = reduceBuff.getData(); //gets a word (consumes)
						System.out.println("RT - Calculating Inverted Index... Here is the word: "+current.word);
						ArrayList<fileLine> start; //declares an ArrayList
						
						/* if the word already exist in 'map' (inverted index), then look up the
						 * word in the inverted index and add the new filename and line number
						 * combination to its already existing fileLine ArrayList */
						if (map.containsKey(current.word)){
							map.get(current.word).add(current.stats);
						}
						//Calculates this thread's invertedIndex
						if (invertedIndex.containsKey(current.word)){
							if(invertedIndex.get(current.word).contains(current.stats)){}
							else
								invertedIndex.get(current.word).add(current.stats);
						}
						/* if the word does not exist, then create a new ArrayList with the word
						 * and the filename and line number combination and then add the word 
						 * and the ArrayList of the fileLine object to the TreeMap 'map' */
						else{
							start= new ArrayList<fileLine>();
							start.add(current.stats);
							map.put(current.word, start); //global invertedIndex
							invertedIndex.put(current.word, start); //thread's inverted Index
						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			//prints out the inverted index created by this thread
			System.out.println("Reduce Thread #"+ threadID +"'s inverted index: "+invertedIndex);
		}
	}

	public static void main (String[]args) throws Exception{
		MapReduce outer= new MapReduce(); //creates a MapReduce object
		
		//Request inputs from the keyboard: the number of MapThreads, and the number of ReduceTjreads
		System.out.println("Enter on separte lines: \na. The number of Map Threads \nb. The number of Reduce threads");
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		int mapNum = Integer.parseInt(inFromUser.readLine());//number of Map Threads
		int reduceNum = Integer.parseInt(inFromUser.readLine());//number of Reduce Threads
		inFromUser.close(); //close the BufferReader
	
		MapThread[] mthreadArray = new MapThread[mapNum]; //array of MapThreads
		ReduceThread[] rthreadArray = new ReduceThread[reduceNum]; //array of ReduceThreads
		Buffer[] buff = new Buffer[reduceNum]; //array of Buffers
		SharedCounter counter = outer.new SharedCounter(mapNum); //shared data

		/* spawns all reduceThread Buffers. Each buffer corresponds to a ReduceThread. For
		 * example, buff[0] is the buffer for rthreadArrayy[0] (array of ReduceThreads) */
		for (int b=0; b<reduceNum; b++)
			buff[b] = outer.new Buffer();

		/* Spawns a map thread for each file1, file2.... file_mapNum, dds them to the MapThread
		 * array and start all the threads. */
		for(int m=0; m<mapNum;m++){
			String filename = "foo"+(m+1)+".txt";
			mthreadArray[m]=outer.new MapThread(buff, counter, filename);
			mthreadArray[m].start();
		}

		/* spawns reduce threads 0.... reduceNum, adds them to the ReduceThread array and start
		 * the threads */
		for (int r=0; r<reduceNum; r++){
			rthreadArray[r] = outer.new ReduceThread(buff[r], counter, r);
			rthreadArray[r].start();
		}
		
	}

}
