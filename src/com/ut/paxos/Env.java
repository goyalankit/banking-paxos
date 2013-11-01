package com.ut.paxos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class    Env {
	Map<ProcessId, Process> procs = new HashMap<ProcessId, Process>();
	public final static int nAcceptors = 3, nReplicas = 10, nLeaders = 2, initBalance = 150;;
    public static int nRequests = 1;
    private Set<Account> accounts;
    private HashMap<Integer, String> requests;

	synchronized void sendMessage(ProcessId dst, PaxosMessage msg){
		Process p = procs.get(dst);
		if (p != null) {
			p.deliver(msg);
		}
	}

	synchronized void addProc(ProcessId pid, Process proc){
		procs.put(pid, proc);
		proc.start();
	}

	synchronized void removeProc(ProcessId pid){
		procs.remove(pid);
	}

	void run(String[] args){
		ProcessId[] acceptors = new ProcessId[nAcceptors];
		ProcessId[] replicas = new ProcessId[nReplicas];
		ProcessId[] leaders = new ProcessId[nLeaders];
        accounts = new HashSet<Account>();
        requests = new HashMap<Integer, String>();

        initCommands();

		for (int i = 0; i < nAcceptors; i++) {
			acceptors[i] = new ProcessId("acceptor:" + i);
			Acceptor acc = new Acceptor(this, acceptors[i]);
		}
		for (int i = 0; i < nReplicas; i++) {
			replicas[i] = new ProcessId("replica:" + i);
			Replica repl = new Replica(this, replicas[i], leaders);
            for(int j=0;j<5;j++)
                repl.accounts.add(new Account(j,initBalance));
		}
		for (int i = 0; i < nLeaders; i++) {
			leaders[i] = new ProcessId("leader:" + i);
			Leader leader = new Leader(this, leaders[i], acceptors, replicas);
		}



		for (int i = 1; i < nRequests; i++) {
			ProcessId pid = new ProcessId("client:" + i);
			for (int r = 0; r < nReplicas; r++) {
                System.out.println("making request "+ requests.get(i));
                sendMessage(replicas[r],
					new RequestMessage(pid, new Command(pid, 0, requests.get(i))));
			}
		}
	}


    public void initCommands(){

        requests.put(1, "CMD D 1 100");
        requests.put(2, "CMD D 1 100");
        requests.put(3, "CMD T 1 2 100");
        requests.put(4, "CMD Q 2");
        requests.put(5, "CMD Q 1");
        requests.put(6, "CMD w 1 200");
        requests.put(7, "CMD Q 1");
        requests.put(8, "CMD w 1 500");

        nRequests = requests.size() + 1;
    }

	public static void main(String[] args){
		new Env().run(args);
	}
}
