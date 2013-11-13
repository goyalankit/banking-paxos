package com.ut.paxos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Leader extends Process {
    ProcessId[] acceptors;
    ProcessId[] replicas;
    ProcessId[] leaders;

    BallotNumber ballot_number;
    String logFile;
    boolean active = false;

    Map<Integer, Command> proposals = new HashMap<Integer, Command>();
    Map<Command, Integer /* Maximum slot number */> readSlot = new HashMap<Command, Integer>();

    boolean isWaiting;

    private Monitor monitor;
    private HeartBeat heartBeat;

    private Set<ProcessId> deadProcesses;
    private ProcessId currentActiveLeader;

    //hearbeat variables
    private static final int heartbeatDelayMin = 1000;
    private static final int heartbeatDelayMax = 3000;

    boolean isIgnoring;

    //test variables
    boolean causeLeaderPingTimout;
    boolean singleLeaderOnInception;

    //Lease Variable
    private boolean activeLease;
    Map<Command, Integer /* Maximum slot number */> readAcks = new HashMap<Command, Integer>();
    int currentSlotNumber;
    long leaseTimeout = 7000;
    long leaseEnd;
    int newLeader;

    public Leader(Env env, ProcessId me, ProcessId[] acceptors,
                  ProcessId[] replicas) {
        this.env = env;
        this.me = me;
        ballot_number = new BallotNumber(0, me);
        this.acceptors = acceptors;
        this.replicas = replicas;
        this.isIgnoring = false;
        this.isWaiting = false;
        env.addProc(me, this);
        this.deadProcesses = new HashSet<ProcessId>();
        this.currentActiveLeader = me;
        this.causeLeaderPingTimout = false;
        this.singleLeaderOnInception = true;
        this.logFile = "logs/"+me.name.replace(":", "") + ".log";
        this.activeLease = false;
        this.newLeader = 0;
    }

    public void body() {
        boolean init = true;

        System.out.println("Here I am: " + me);

//		new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number),
//			me, acceptors, ballot_number);

        while (!isWaiting) {
            PaxosMessage msg = getNextMessage();

                if(isWaiting)
                    return;

                if(init && !me.equals(leaders[newLeader])){
                    if(!isIgnoring)
                        setIgnoring(true);
                    startMonitoring(leaders[newLeader]);
                    init = false;
                    continue;
                }


            if (msg instanceof HearBeatMessage) {
                HearBeatMessage m = (HearBeatMessage) msg;
                HearBeatMessageResponse hearBeatMessageResponse = new HearBeatMessageResponse(me);
                if (allowedToSendHeartBeat(m.src))
                    sendMessage(m.src, hearBeatMessageResponse);

            } else if (msg instanceof HearBeatMessageResponse) {

                HearBeatMessageResponse m = (HearBeatMessageResponse) msg;
                if (monitor != null && monitor.getCurrent().equals(m.src)) {
                    monitor.resetTimeout();
                } else
                    System.err.println("Monitor process is not running");

                //we are cool keep waiting.
            } /*else if (msg instanceof ReadOnlyAckMessage) {
                ReadOnlyAckMessage m = (ReadOnlyAckMessage) msg;
                if (!readSlot.isEmpty()) {
                    readSlot.remove(m.command);
                    System.err.println("read slot size " + readSlot.size());
                }
                readAcks.put(m.command, 1);
            }*/ else if (msg instanceof ProposeMessage) {
                ProposeMessage m = (ProposeMessage) msg;

                writeLog(me+" proposal received from "+m.src);

                if (isReadOnly(m.command) && m.slot_number == -1) {
                    handleReadOnlyProposal(m);
                    continue;
                }

                if (!proposals.containsKey(m.slot_number)) {
                    proposals.put(m.slot_number, m.command);
                    if (active && !isIgnoring) {
                        new Commander(env,
                                new ProcessId("commander:" + me + ":" + ballot_number + ":" + m.slot_number),
                                me, acceptors, replicas, ballot_number, m.slot_number, m.command);
                    } else if (!isIgnoring) {
                        new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number),
                                me, acceptors, ballot_number);
                    }
                }
            } else if (msg instanceof AdoptedMessage) {

                AdoptedMessage m = (AdoptedMessage) msg;

                System.out.println("Adopted by " + m.src);

                writeLog(me+" Adobted by "+m.src);
                if (ballot_number.equals(m.ballot_number)) {
                    Map<Integer, BallotNumber> max = new HashMap<Integer, BallotNumber>();
                    for (PValue pv : m.accepted) {
                        BallotNumber bn = max.get(pv.slot_number);
                        if (bn == null || bn.compareTo(pv.ballot_number) < 0) {
                            max.put(pv.slot_number, pv.ballot_number);
                            proposals.put(pv.slot_number, pv.command);
                        }
                    }

                    if(m.awardedLease){
                        activeLease = true;
                        leaseEnd =  m.currentTimeStamp;
                        if(!proposals.isEmpty()){
                            int mx = Collections.max(proposals.keySet());
                            for(Command cmd : readSlot.keySet() ){
                                readSlot.put(cmd, mx);
                            }
                        }
                    }

                    System.out.println(me+" proposal size "+proposals.size()+" read slot emptY? "+readSlot.isEmpty());
                    if(!readSlot.isEmpty())
                        System.out.println("read slot was not empty "+Collections.min(readSlot.values()));

                    if(proposals.isEmpty()){
                        if (!readSlot.isEmpty()) {
                            if(activeLease && System.currentTimeMillis() < leaseEnd){
                                currentSlotNumber = Collections.min(readSlot.values());
                                executeReadOnlyCommands();
                            }
                            else{
                                new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number), me, acceptors, ballot_number, true);
                            }
                        }
                    }

                    for (int sn : proposals.keySet()) {
                        if (readSlot.isEmpty()){
                            new Commander(env, new ProcessId("commander:" + me + ":" + ballot_number + ":" + sn), me, acceptors, replicas, ballot_number, sn, proposals.get(sn));
                        }
                        else if (!readSlot.isEmpty() && sn < Collections.min(readSlot.values())) {
                            new Commander(env, new ProcessId("commander:" + me + ":" + ballot_number + ":" + sn), me, acceptors, replicas, ballot_number, sn, proposals.get(sn));
                        } else if (!readSlot.isEmpty()) {
                            if(activeLease && System.currentTimeMillis() < leaseEnd){
                                currentSlotNumber = Collections.min(readSlot.values());
                                executeReadOnlyCommands();
                            }else{
                                new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number), me, acceptors, ballot_number, true);
                            }
                        }
                    }
                    active = true;
                }
            } else if (msg instanceof PreemptedMessage) {
                PreemptedMessage m = (PreemptedMessage) msg;
                writeLog(me+" Preempted by "+m.ballot_number.leader_id + " with ballot number"+ m.ballot_number + " my ballot number: "+ballot_number  );
                if (ballot_number.compareTo(m.ballot_number) < 0 || m.ballot_number.round == -1) {
                    ballot_number = new BallotNumber(m.ballot_number.round + 1, me);

                    if (!isIgnoring && !me.equals(leaders[newLeader])) {
                        setIgnoring(true);

                        if (monitor != null)
                            monitor.kill();

                        startMonitoring(leaders[newLeader]);
                        continue;

                    } else if (deadProcesses.contains(m.ballot_number.getLeader_id())) {
                        new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number),
                                me, acceptors, ballot_number);
                        active = false;
                    }
                }
            } else {
                System.err.println("Leader: unknown msg type");
            }
        }
    }

    private void handleReadOnlyProposal(ProposeMessage m) {
        if (!readSlot.containsKey(m.command)){
            if(!isIgnoring)
                new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number), me, acceptors, ballot_number, true);
            if(proposals.isEmpty())
                readSlot.put(m.command, 0);
            else
                readSlot.put(m.command, Collections.max(proposals.keySet()));
        }
    }

    public void executeReadOnlyCommands() {

//        TODO Test Case 5: Leader 1 dies before sending read only decision to replicas.
//        if(me.name.equals("leader:0")){
//            setWaiting(true);
//            return;
//        }


        Set<Command> commandsSent = new HashSet<Command>();
        for (Command cmd : readSlot.keySet()) {
            for (int i = 0; i < replicas.length; i++) {

                if (currentSlotNumber >= readSlot.get(cmd)) {
                    sendMessage(replicas[i], new ReadOnlyCommandMessage(me, cmd, currentSlotNumber));
                    commandsSent.add(cmd);
                }
            }
        }

        for (Command c : commandsSent)
            readSlot.remove(c);

        if(!proposals.isEmpty())
            new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number), me, acceptors, ballot_number);
    }

/*
    class ReadExecutor extends Thread{
        public void run(){
            System.err.println("Executing read only commands");

            for(Command cmd : readSlot.keySet()){
                int slot = readSlot.get(cmd);
                ackExceptedTill = System.currentTimeMillis() + ackTimeout;
                int i = 0;
                //while (readSlot.containsKey(cmd)) {
                sendMessage(replicas[i], new ReadOnlyCommandMessage(me, cmd, slot));
                while(ackExceptedTill > System.currentTimeMillis()){
                    if(!readAcks.isEmpty() && readAcks.get(cmd) == 1){
                        readSlot.remove(cmd);
                        break;
                    }
                }
                //}
            }
        }
    }
*/


    public void startMonitoring(ProcessId leader) {

        currentActiveLeader = leader;

        heartBeat = new HeartBeat(leader);
        heartBeat.start();

        monitor = new Monitor(System.currentTimeMillis(), this, currentActiveLeader);
        monitor.start();

    }

    class Monitor extends Thread {
        private long lastHeartBeat;
        private Leader parent;
        private ProcessId current;
        private boolean isRunning;

        Monitor(long lastHeartBeat, Leader parent, ProcessId current) {
            this.lastHeartBeat = lastHeartBeat;
            this.parent = parent;
            this.current = current;
            this.isRunning = true;

        }

        public void run() {
            //System.err.println("Monitor started in " + parent.me);
            while (isRunning) {
                if (lastHeartBeat < System.currentTimeMillis() - 3000) {
                    break;
                }
                yield();
            }
            newLeader++;

            if(me.equals(leaders[newLeader])){
                System.out.println(me+" I am the new leader...hence going to cleanup the dead");
                parent.setIgnoring(false);
                return;
            }else if(newLeader < Env.nLeaders){
                System.out.println(me+" I am not the leader...:(");
                if(!isIgnoring)
                    setIgnoring(true);
                startMonitoring(leaders[newLeader]);
            }
        }

        ProcessId getCurrent() {
            return current;
        }

        public void kill() {
            System.err.println("Monitor Killed");
            this.isRunning = false;
        }

        public void resetTimeout() {
            lastHeartBeat = System.currentTimeMillis();
        }

    }


    class HeartBeat extends Thread {

        private long lastHeartbeat;
        private ProcessId leader;
        Random rand = new Random();
        private boolean isRunning;

        public HeartBeat(ProcessId leader) {
            this.leader = leader;
            this.isRunning = true;
        }

        public void run() {
            int heartbeatDelay = rand.nextInt(heartbeatDelayMax - heartbeatDelayMin) + heartbeatDelayMin;
            while (isRunning) {
                if (heartbeatDelay < System.currentTimeMillis() - lastHeartbeat) {
                    lastHeartbeat = System.currentTimeMillis();
                    HearBeatMessage msg = new HearBeatMessage(me);
                    // System.err.println("Sending heartbeat");
                    sendMessage(leader, msg);
                    heartbeatDelay = rand.nextInt(heartbeatDelayMax - heartbeatDelayMin) + heartbeatDelayMin;
                }
                yield();
            }
        }

        public void kill() {
            this.isRunning = false;
        }

    }

    public void setWaiting(boolean waiting) {
        isWaiting = waiting;
    }


    public void setIgnoring(boolean ignoring) {

        isIgnoring = ignoring;
        System.err.println(me + " set IGNORING " + ignoring);

        if (!ignoring)
            cleanUpAfterDeadLeader();

    }

    public void cleanUpAfterDeadLeader() {

        monitor.kill();
        heartBeat.kill();

        if (!currentActiveLeader.equals(me)) {
            deadProcesses.add(currentActiveLeader);
            currentActiveLeader = me;

            //run scout for all buffered and new proposals.
            new Scout(env, new ProcessId("scout:" + me + ":" + ballot_number), me, acceptors, ballot_number);
        }

    }


    /*Operational Methods*/

    //Method for testing
    public boolean allowedToSendHeartBeat(ProcessId processId) {
        //System.out.println("course leader pingout "+ causeLeaderPingTimout);
        if (causeLeaderPingTimout) {
            if (me.name.equals("leader:0") && processId.name.equals("leader:1"))
                return false;
        }
        return true;
    }

    public void getStatus() {
        System.out.println("Ignore " + isIgnoring + " | Waiting " + isWaiting + " | CurrentLeader " + currentActiveLeader);
    }

    public void setCauseLeaderPingTimout(boolean causeLeaderPingTimout) {
        this.causeLeaderPingTimout = causeLeaderPingTimout;
    }


    public boolean isReadOnly(Command command) {
        try {
            String[] s = ((String) (command.op)).split(" ");
            if (s[1].equalsIgnoreCase("q")) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("invalid command");
        }
        return false;
    }


    public void writeLog(String msg) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true));
            bw.write(msg + "\n");
            bw.flush();
        } catch (IOException io) {
            System.err.println(io.getMessage());
        }
    }

}


