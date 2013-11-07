package com.ut.paxos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

public class Client extends Process {

    //Set<Account> accounts;
    ProcessId[] replicas;
    int numberOfRequests;
    String logFile;

    public void body() {
        System.out.println("Here I am: " + me);
        initCommands();
        while (true){
            PaxosMessage msg = getNextMessage();
            if (msg instanceof ServerResponse) {
                ServerResponse m = (ServerResponse) msg;
                writeLog(m.req_id+": "+m.result);
            }
        }
    }

    public Client(Env env, ProcessId me, Set <Account> accounts, ProcessId[] replicas) {
        this.env = env;
      //  this.accounts = accounts;
        this.me = me;
        this.replicas = replicas;
        this.numberOfRequests = 0;
        System.err.println("Addin client to proc "+me);
        env.addProc(me, this);
        this.logFile = "logs/"+me.name.replace(":", "") + ".log";
    }

    public void initCommands(){
        if(me.name.equals("client:1")){
            sendCommandToReplicas("cmd Q 1");
            sendCommandToReplicas("cmd w 1 20");
            sendCommandToReplicas("cmd D 1 40");
        }else{
            sendCommandToReplicas("cmd Q 1");
            sendCommandToReplicas("cmd D 1 40");
            sendCommandToReplicas("cmd D 1 40");
        }
    }

    public void sendCommandToReplicas(String request) {
        System.out.println("inside cliente");
        for (int r = 0; r < replicas.length; r++) {
            System.out.println("making request " + request);
            sendMessage(replicas[r],
                    new RequestMessage(this.me, new Command(this.me, numberOfRequests, request)));
        }
        numberOfRequests++;
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