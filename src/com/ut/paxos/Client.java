package com.ut.paxos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public class Client extends Process {

    //Set<Account> accounts;
    ProcessId[] replicas;
    int numberOfRequests;
    String logFile;
    HashMap<Integer, Integer> successfulTransactions;
    int latestReqId;

    public void body() {
        System.out.println("Here I am: " + me);
        initCommands();
        while (true){
            PaxosMessage msg = getNextMessage();
            if (msg instanceof ServerResponse) {
                ServerResponse m = (ServerResponse) msg;
                if(!successfulTransactions.containsKey(m.req_id)){
                    System.out.println(me+" Received the response from server for req id "+m.req_id);
                    writeLog(m.req_id+": "+m.result);
                    successfulTransactions.put(m.req_id, 1);
                    latestReqId = m.req_id;
                }
            }
        }
    }

    public Client(Env env, ProcessId me, Set <Account> accounts, ProcessId[] replicas) {
        this.env = env;
//        this.accounts = accounts;
        this.me = me;
        this.replicas = replicas;
        this.numberOfRequests = 0;
        System.err.println("Addin client to proc "+me);
        env.addProc(me, this);
        successfulTransactions = new HashMap<Integer, Integer>();
        this.latestReqId = -1;
        this.logFile = "logs/"+me.name.replace(":", "") + ".log";

    }

    public void initCommands(){
        //testCases(5);
    }

    public void testCases(int num) {
        try {
            switch (num) {
                case 1:
                    //Client 0 proposes to one replica and client 1 to proposes to other replica.
                    //One of the replicas re-proposes and decides a different slot number.
                    //nReplicas: 2, Leaders: 1, Acceptors: 3
                    if (me.name.equals("client:0")) {
                        sendCommandToReplicas("cmd w 1 20", 0, 0);
                    } else {
                        sendCommandToReplicas("cmd w 1 50", 1, 0);
                    }
                    break;
                case 2:
                    //nReplicas: 2, Leaders: 1, Acceptors: 3
                    //Replica 1 get the request after the decision. It doesn't proposes.
                    //Same request to different replicas
                    if (me.name.equals("client:0")) {
                        sendCommandToReplicas("cmd w 1 75", 0, 0);
                        Thread.sleep(4000);
                        sendCommandToReplicas("cmd w 1 75", 1, 0);

                    }
                    break;
                case 3:
                    //nReplicas: 2, Leaders: 1, Acceptors: 3
                    //Replica One doesn't get request from client 1
                    if (me.name.equals("client:0")) {
                        sendCommandToReplicas("cmd w 1 20");
                    }
                    else{
                        sendCommandToReplicas("cmd w 1 30", 0);
                    }
                    break;
                case 4:
                    //nReplicas: 2, Leaders: 2, Acceptors: 3
                    //Replica One doesn't get request from client 1
                    if (me.name.equals("client:0")) {
                        sendCommandToReplicas("cmd w 1 20", 0);
                    }

                    break;
                case 5:
                    if (me.name.equals("client:0")) {
                        sendCommandToReplicas("cmd q 1");
                    }   else{
                        Thread.sleep(2000);
                        sendCommandToReplicas("cmd w 1 20");
                    }
                    break;
                default:
                    System.err.println("No test case exists with this number ");
                    break;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void sendCommandToReplicas(String request) {
        if(!canSendCommand(request, numberOfRequests)){
            System.err.println(me+" Cannot send command yet. Waiting for the response to previous command.");
            return;
        }

        System.out.println("inside cliente");
        for (int r = 0; r < replicas.length; r++) {
            //System.out.println("making request " + request);
            sendMessage(replicas[r],
                    new RequestMessage(this.me, new Command(this.me, numberOfRequests, request)));
        }
        numberOfRequests++;
    }

    public void sendCommandToReplicas(String request,int replicaNumber, int req_id){

        if(!canSendCommand(request, req_id)){
            System.err.println(me+" Cannot send command yet. Waiting for the response to previous command.");
            return;
        }
        sendMessage(replicas[replicaNumber],
                new RequestMessage(this.me, new Command(this.me, req_id, request)));
    }

    public void sendCommandToReplicas(String request, int replicaNumber){
        this.sendCommandToReplicas(request, replicaNumber ,numberOfRequests);
        numberOfRequests++;
    }


    public boolean canSendCommand(String request, int proposed_req_id){
        System.err.println("proposed req id "+proposed_req_id + " actual req id "+latestReqId);
        if((proposed_req_id - 1) == latestReqId){
            return true;
        }
        return false;
    }


    public void writeLog(String msg)
    {
        try
        {
            BufferedWriter bw = new BufferedWriter(new FileWriter(logFile,true));
            bw.write(msg+"\n");
            bw.flush();
        }
        catch(IOException io)
        {
            System.err.println(io.getMessage());
        }
    }
}
