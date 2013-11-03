package com.ut.paxos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Env {
    Map<ProcessId, Process> procs = new HashMap<ProcessId, Process>();
    public final static int nAcceptors = 3, nReplicas = 5, nLeaders = 4, initBalance = 150;
    ;
    public static int nRequests = 1;
    private HashMap<Integer, String> requests;
    private ProcessId[] rdupRplicas;
    private ProcessId[] leaders;

    synchronized void sendMessage(ProcessId dst, PaxosMessage msg) {
        Process p = procs.get(dst);
        if (p != null) {
            p.deliver(msg);
        } else {
            //System.err.println("not delivering message to "+dst + " " + procs.toString());
        }
    }

    synchronized void addProc(ProcessId pid, Process proc) {
        procs.put(pid, proc);
        proc.start();
    }

    synchronized void removeProc(ProcessId pid) {
        procs.remove(pid);
    }

    void run(String[] args) {
        ProcessId[] acceptors = new ProcessId[nAcceptors];
        ProcessId[] replicas = new ProcessId[nReplicas];
        leaders = new ProcessId[nLeaders];
        requests = new HashMap<Integer, String>();
        rdupRplicas = new ProcessId[nReplicas];

        //give commands
        initCommands();

        for (int i = 0; i < nAcceptors; i++) {
            acceptors[i] = new ProcessId("acceptor:" + i);
            Acceptor acc = new Acceptor(this, acceptors[i]);
        }
        for (int i = 0; i < nReplicas; i++) {
            replicas[i] = new ProcessId("replica:" + i);
            rdupRplicas[i] = replicas[i];
            Replica repl = new Replica(this, replicas[i], leaders);

            //give account information to each replica
            for (int j = 0; j < 5; j++)
                repl.accounts.add(new Account(j, initBalance));

        }
        for (int i = 0; i < nLeaders; i++) {
            leaders[i] = new ProcessId("leader:" + i);
            Leader leader = new Leader(this, leaders[i], acceptors, replicas);
        }

        for (int i = 1; i < nRequests; i++) {
            ProcessId pid = new ProcessId("client:" + i);
            for (int r = 0; r < nReplicas; r++) {
                System.out.println("making request " + requests.get(i));
                sendMessage(replicas[r],
                        new RequestMessage(pid, new Command(pid, 0, requests.get(i))));
            }
        }
    }


    public void initCommands() {

        requests.put(1, "CMD D 1 100");
//        requests.put(2, "CMD D 1 100");
//        requests.put(3, "CMD T 1 2 100");
//        requests.put(4, "CMD Q 2");
//        requests.put(5, "CMD Q 1");
//        requests.put(6, "CMD w 1 200");
//        requests.put(7, "CMD Q 1");
//        requests.put(8, "CMD w 1 5");
//        requests.put(9, "CMD Q 1");
//        requests.put(10, "CMD w 1 0");

        nRequests = requests.size() + 1;
    }

    public static void main(String[] args) {
        Env env = new Env();
        env.run(args);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String[] s = new String[0];
            try {
                s = in.readLine().split(" ", 2);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String cmd = s[0];
            String arg = s.length > 1 ? s[1] : null;

            if (cmd.equalsIgnoreCase("propose")) {
                ProcessId pid = new ProcessId("client:" + ++nRequests);
                for (int r = 0; r < nReplicas; r++) {
                    env.sendMessage(env.rdupRplicas[r],
                            new RequestMessage(pid, new Command(pid, 0, s[1])));
                }
            } else if (cmd.equalsIgnoreCase("stop")) {
                Leader l = (Leader) env.procs.get(env.leaders[Integer.parseInt(s[1].trim())]);
                l.setWaiting(true);
            } else if (cmd.equalsIgnoreCase("status")) {
                System.out.println("keys " + env.procs.toString());
                Leader l = (Leader) env.procs.get(env.leaders[Integer.parseInt(s[1].trim())]);
                if(l != null)
                    l.getStatus();
                else
                    System.err.println("Process is dead");
            } else if(cmd.equals("")){
                //Just pressing enter or something. don't do anything
            } else{
                System.err.println("Invalid Command");
            }
        }
    }
}

