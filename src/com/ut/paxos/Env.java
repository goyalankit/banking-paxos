package com.ut.paxos;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Env {
    Map<ProcessId, Process> procs = new HashMap<ProcessId, Process>();
    public final static int nAcceptors = 3, nReplicas = 5, nLeaders = 4, nClients = 2, initBalance = 150, nAccounts = 5;

    public static int nRequests = 1;
    private HashMap<Integer, String> requests;
    private ProcessId[] rdupRplicas;
    private ProcessId[] leaders;
    private ProcessId[] clients;

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
        clients = new ProcessId[nClients];

        //give commands
        //initCommands();

        for (int i = 0; i < nAcceptors; i++) {
            acceptors[i] = new ProcessId("acceptor:" + i);
            Acceptor acc = new Acceptor(this, acceptors[i]);
        }



        for (int i = 0; i < nReplicas; i++) {
            replicas[i] = new ProcessId("replica:" + i);
            rdupRplicas[i] = replicas[i];
            Replica repl = new Replica(this, replicas[i], leaders);

            //give account information to each replica
            for (int j = 0; j < nAccounts; j++) {
                Account ac = new Account(j, initBalance);
                repl.accounts.add(ac);
            }
        }

        for (int i = 0; i < nLeaders; i++) {
            leaders[i] = new ProcessId("leader:" + i);
            Leader leader = new Leader(this, leaders[i], acceptors, replicas);
        }

        for (int i = 0; i < nClients; i++) {
            clients[i] = new ProcessId("client:" + i);
            Client client = new Client(this, clients[i], null, replicas);
            //client.sendCommandToReplicas("CMD D 1 100");
        }

        for (int i = 0; i < nReplicas; i++) {
            ((Replica)procs.get(replicas[i])).clients = clients;
        }

    /*
        for (int i = 1; i < nRequests; i++) {
            ProcessId pid = new ProcessId("client:" + i);
            for (int r = 0; r < nReplicas; r++) {
                System.out.println("making request " + requests.get(i));
                sendMessage(replicas[r],
                        new RequestMessage(pid, new Command(pid, 0, requests.get(i))));
            }
        }
*/

    }


    public void initCommands() {
        requests.put(1, "CMD D 1 100");
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

                //propose 1 cmd q 1
//                ProcessId pid = new ProcessId("client:" + ++nRequests);
                String[] s1 = new String[0];
                s1 = s[1].split(" ", 2);

                Client client = (Client)env.procs.get(env.clients[Integer.parseInt(s1[0].trim())]);
                client.sendCommandToReplicas(s1[1]);
            } else if (cmd.equalsIgnoreCase("stop")) {
                Leader l = (Leader) env.procs.get(env.leaders[Integer.parseInt(s[1].trim())]);
                l.setWaiting(true);
            } else if (cmd.equalsIgnoreCase("rep_dec")) {
                    Replica r = (Replica) env.procs.get(env.rdupRplicas[Integer.parseInt(s[1].trim())]);
                    r.rep_dec();
            } else if(cmd.equalsIgnoreCase("clear")){
                File dir = new File("logs");
                for(File file: dir.listFiles()) file.delete();
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

