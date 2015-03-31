Xian Chen
377 Lab2: Threads and Synchronization
Documentation

What is in the .zip:
Test files: foo1.txt, foo2.txt
Outputs: SampleOutput_1.txt, SampleOutput_2.txt
The Program: MapReduce.java


Table of Contents
1. Introduction
2. General Design Concept
3. The Program: A Detailed Outline
	a. Global Variables
	b. Main Method
		i. Design Alternatives
	c. fileLine Class
	d. Word Class
e. Buffer Class
	i. getData() Method
	ii. setData() Method
	iii. Synchronization
	f. SharedCounter Class
		i. Motivation
	g. MapThread Class
	h. Reduce Thread Class
4. Weaknesses
5. References


Introduction
The purpose of this document is to outline my design choices when implementing MapReduce using threads and synchronization. This document has two parts first briefly explain the general design of this program and then it will provide a detail explanation of each class that were created and the motivation for the design choices chosen. Weaknesses and References are listed at the end. 


General Design Concept
The number of Map Threads corresponds to the number of files (foo1.txt…. foon.txt) and each Map Thread is to process its corresponding file. Each file is assumed to contain one word per line. The Map Threads is to read each word and to hash the word to a Reduce Thread in which the word’s inverted index will be computed. This is done by using a bounded buffer. Each Reduce Thread has a corresponding bounded buffer which it reads from. Therefore, when the Map Threads passes a word to a Reduce Thread, it simply adds the word into the Reduce Thread’s bounded buffer. The bounded buffer has a maximum size limit (10) and minimum size limit (0), therefore a Map Thread cannot add a new entry to a buffer that is at its maximum capacity and a Reduce Thread cannot “consume” from a buffer that is empty-- the Map Thread will have to wait until the buffer size decreases and the Reduce Thread have to wait until the buffer is not empty before it can continue. When a Reduce Thread finishes, it prints out the inverted index it has created. 

This is a “Producer, Consumer” problem where the Map Thread is the Producer and the Reduce Thread is the Consumer. The Map Threads produces information and Reduce Threads consume them. 


The Program: A Detailed outline
Extras: 
TreeMap map- Stores the global inverted index. The TreeMap contains Strings (words) as keys and an ArrayList of fileLine objects (which will be later discussed in this report) as values. 

Main Method:
This program spawns multiple Map Threads and Reduce Threads by taking two numeric inputs from the keyboard. The main method of the MapReduce program takes two numeric inputs from the keyboard using a BufferedReader. It is assumed that the user will enter two numbers (not chars, Strings, doubles... etc.) and that the user will enter a number per line.

	Design Alternatives:
	Scanner: Instead of a BufferedReader I could use a Scanner object. However, since I will also be reading a file using a BufferedReader, I decided that rather than importing another class to read input from the keyboard (Scanner), I would simply use the same class (BufferedReader).

	JOptionPane: Similarly, if I used JOptionPane, I would have to import another class. To keep the program as lightweight as possible, I decided to use BufferedReader.

The first number entered corresponds to the number of Map Threads and the number of Map Threads corresponds to the number of files (foo1.txt…. foon.txt). Each Map Thread is to process its corresponding file and it is assumed that the files to be read are named foo1.txt, foo2.txt… etc. and that each file contains one word per line. 
The second number entered corresponds to the number of Reduce Threads. Since each Reduce Thread reads from a unique buffer, the second number entered also corresponds to the number of buffers that will be created. 

The main method then initialized three arrays, an array of Map Threads, Reduce Threads, and Buffers. It also creates a SharedCounter that is initialized to the number of Map Threads created. I will discuss the SharedCounter class later in the report. 

The program first creates all the Buffer objects and stores it in the Buffer array. The program then created all the Map Threads, passing to each of the Map Threads the array of Buffers, the SharedCounter, and a file to be read, adds them to the Map Thread array, and starts them.  Next, the program spawns all the Reduce Threads, adds them to the Reduce Thread array, passes each Reduce Thread a unique buffer, and the Reduce Thread id number (index in the Reduce Thread array) and starts them.

fileLine Class:
A fileLine object stores a filename and a line number.  

Word Class:
A Word object contains a String and a fileLine object. The String is the word that is read from a file and the fileLine object stores the location in which the word can be found (the file name and the line number). 

Buffer Class:
The Buffer class is the bounded buffer. It contains a queue of Word objects in which the Reduce Thread is to consume (pop) from and the MapThread is to add (push) into. It also contains an integer that states the maximum size the queue can be. The class contains two methods: getData() and setData(). 

	getData() Method: 
	getData() is called by the Reduce Thread to obtain the Word object in which it uses to construct the inverted index. If the queue size is empty, then the thread waits. Otherwise, the thread is notified (woken up) since there is something to be 'consumed.’ An entry is then popped from the queue and is returned to the requesting Reduce Thread.

	setData() Method:
	setData is used by Map Threads to pass off information to the Reduce Thread to be processed. If the Buffer’s queue is at its maximum size (10), then a MapThread has to wait until the queue size decreases before adding an entry, otherwise a Map Thread can add an entry to the queue. A single waiting thread that is waiting to access the critical area is then notified (woken up).

	Synchronization:
	Since both Map and Reduce Threads have access to a Buffer’s queue, it is possible that the queue size can be incremented and decremented at the same time. This can cause problems! Since we do not want more than one thread to be able to change the size of the queue at any point in time, the getData() and setData() methods are synchronized. If a thread calls one of the synchronized methods, the thread now owns a “lock” for the Buffer. This ensures that only one thread at a time is running within the critical area. For example, if a Reduce Thread calls getData() to “pop” a Word object from the queue while a Map Thread is adding Word objects to the Buffer’s queue, the Reduce Thread will have to wait until the Map Thread finishes. 

SharedCounter Class:
A SharedCounter object contains a single integer. In this program, the only purpose of the SharedCounter class is to keep track of the number of active Map Threads. Once a Map Thread has completed, the Map Thread decrements the SharedCounter. Reduce Threads keeps track of the counter. If the SharedCounter is zero, then the Reduce Threads know that all the Map Threads have finished and therefore it has finish after its Buffer is empty.

	Motivation: 
	The SharedCounter ensures that the Reduce Threads finish correctly. Before adding a SharedCounter, the Reduce Thread would assume it is finish when its buffer is empty-- however, if the Reduce Thread assumes it has finish, and if there still exist some active Map Threads, then the active Map Threads might push Word objects into the Reduce Thread’s buffer! Therefore, the inverted index for those Word object will never be computed. Therefore, for the Reduce Threads to correctly finish, it has to check that there is no more active Map Threads-- this functionality is provided by the Shared Counter class. 

MapThread Class:
A Map Thread opens the file that was passed to it and reads in a word line by line (assuming one word per line). For each word, it creates a fileLine object that stores the filename and the line number of the word. Each word is then hashed by calling the hashing method, hash(). The hashing method hashes a word to a number of a specific reduce thread to be processed (For example, if there are 3 Reduce Threads, the hashing function will hash a word to either the number 0, 1, or 2 corresponding to the first, second, and third Reduce Thread respectively). The Map Thread will then add the word to a Buffer object at the Buffer array index of the hashed value, which will be read by that Buffer object’s corresponding Reduce Thread (ex. BufferArray[1] is read by ReduceThreadArray[1]). If the Map Thread has completed reading and hashing all the words, then it has finished. It will then decrement the SharedCounter. 

ReduceThread Class:
A Reduce Thread contains a TreeMap, a Buffer, and a SharedCounter. In its run() method, the Reduce Thread attempts to process information from its Bounded Buffer. If its buffer is not empty, then the Reduce Thread will call getData() on its buffer to “pop” off the Word object (which contains a String word and a fileLine object) from the buffer’s queue. If the word observed from the Word object, already exist in the inverted index then the Reduce Thread will access the word in the TreeMap and append a new fileLine object to the word’s corresponding ArrayList. If the word does not already exist in the inverted index, then the Reduce Thread will create a new ArrayList, append the fileLine object from the Word object to the ArrayList, and add (put) the word and the ArrayList to the inverted index (TreeMap). While the Reduce Thread is updating its inverted index, it is also updating the Global Inverted Index, which contains all the computed inverted index to this point.
Once a Reduce Thread finishes-- which it can determine by checking if the SharedCounter is zero and if its buffer is empty, then it prints the inverted index it has created.


Weaknesses:
There are a three weaknesses that I could determine in the program.
1. Assuming two numeric inputs on separate lines from the keyboard. 
According to the assignment, I am to “assume that a valid integer is provided by the user.” Therefore, the program does not check for invalid inputs such as characters. Also, I am assuming that a user is entering the number of Map Thread and Reduce Threads on separate lines. If the two numbers are entered on the same line, the program will break. 
2. Assuming correct file names and correct file numbers
I am assuming in my program that the files names are: foo1.txt, foo2.txt, … foon.txt. If the file is not name like so, then the program will break. Also I am assuming that the number of Mao Threads entered is equal to the number of files available. If the number of Map Thread entered from the keyboard is greater than the number of files, then the program will break. 
3. Assuming one word per line
According to the assignment, “assume that each file contains exactly one word per line.” If this assumption does not hold, the program will give an incorrect output. 


References:
1. Creating Java Threads
http://docs.oracle.com/javase/tutorial/essential/concurrency/runthread.html
2. Java Queue
http://docs.oracle.com/javase/7/docs/api/java/util/Deque.html
