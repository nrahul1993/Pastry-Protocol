package pastry

import akka.actor._
import scala.math._
import scala.util.Random
import akka.actor.Actor._
import java.util.UUID
import java.lang.Long
import scala.collection.mutable._
import java.security.MessageDigest
import scala.collection.immutable.TreeMap
import java.io.PrintStream
import java.io.FileOutputStream

object pastryProjBonus {
  //System.setOut(new PrintStream(new FileOutputStream("PastryNetworkFlowFailure.txt")));
  val system =ActorSystem("ThePastry")
  case class message(index:Int,from:String,nodeID:String,messagekey:String,path:String,hopnum:Int,NetWork:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class messageTransmit(index:Int,nodeID:String,messagekey:String,path:String,hopnum:Int)
  case class finish(indexID:Int,sumhops:Int)
  case class Init(NetWork:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class sendMessage(network:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class Failure(cat:String,seq:Int,from:String,Init:String,key:String,path:String,hops:Int,network:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class Wakeup(from:String)
  case class Contact(seq:Int,from:String,init:String,key:String,path:String,hops:Int,network:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class fconn(NetWork:HashMap[String, ActorRef],mymap:TreeMap[Int,String],wakeup:Int)
  case class fconnp(NetWork:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class setMode(a:Int,b:List[(String,Int)])
  case class failconf(id1:String,id2:String,fflag:Int)
  val bits=32
  val b=4
  val L=math.pow(2,b).toInt
  val maxlen=math.pow(2,bits).toLong
  var failuremode=false

  def main(args: Array[String]): Unit = {
    
    if(args.length < 2){
      println("Need two inputs!")
      return
    }
    if(args.length==3 && args(2)=="failure"){
       failuremode = true
    }
    val numNodes = args(0).toInt
    val numRequests = args(1).toInt
    var master=system.actorOf(Props(new Master(numNodes,numRequests,failuremode)))  
  }
  def getIp(network: HashMap[String,ActorRef]): String = {
    val ip = scala.util.Random.nextInt(256) + "." + scala.util.Random.nextInt(256) + "." + scala.util.Random.nextInt(256) + "." + scala.util.Random.nextInt(256)
    if (!network.contains(ip)) ip else getIp(network)
  }
  
     def md5(num:Long): String ={
	 val leng = bits/b
	 val digit = Long.toString(num,math.pow(2,b).toInt)
	 var spl = ""
	 for(i <- 0 until leng-digit.length){
	   spl = spl+"0"
	 }
	 spl+digit
  }
  class Master(numNodes: Int, numRequests:Int,failuremode:Boolean) extends Actor with ActorLogging {
    var count = 0
    var totalhops=0
     var NetWork = HashMap.empty[String, ActorRef] 
    var mymap = new TreeMap[Int,String]
   
  

    override def preStart() {
        for (i <- 0 until numNodes){
                var IP=getIp(NetWork)
                var ID=md5((math.random*maxlen).toLong)
                  mymap +={i->ID}
                
                var ss=i
                var node=system.actorOf(Props(new Node(ss,ID, numRequests, self)))
               NetWork +={ID->node}
                                
        }
     for(allnodes<-NetWork.keysIterator){NetWork(allnodes)! Init(NetWork,mymap)}   
     if(failuremode){      
    	 		 val wakeup = 2
    			 val n1 = (math.random*mymap.size+1).toInt
    			 NetWork(mymap(n1)) ! fconn(NetWork,mymap,wakeup)
                 var np1 = (math.random*mymap.size+1).toInt
                 while(np1==n1){ np1 = (math.random*mymap.size+1).toInt }
                 NetWork(mymap(np1)) ! fconnp(NetWork,mymap)
                 
         }   
    }
    var failcount=0
    var id1="";var id2="";var id3="";var id4=""
   def receive = {
       case failconf(n1,n2,flag)=>{
         if (flag==1){var id1=n1; var id2=n2;failcount =failcount+1}
         if (flag==2){var id3=n1; var id42=n2;failcount =failcount+1}
         if (failcount==2){
             var failnd = (math.random*mymap.size+1).toInt
             var failndID = mymap(failnd)
             while(List(id1,id2,id3,id4).contains(failndID)){ failnd = (math.random*mymap.size+1).toInt; failndID= mymap(failnd)}
             NetWork(failndID)!setMode(2,null)
             println("Node dies: N" + failnd + "(" + failndID + ")\n")
         }
         
       }
       case finish(nodeindex,nodehop) =>{
               totalhops+=nodehop
               count+=1
               if (count==numNodes){
                 println("the whole network's average hops is :"+(totalhops/(numRequests.toDouble*numNodes.toDouble)))
                 for(allnode<-NetWork.keysIterator){NetWork(allnode)!"stop"}
                 System.exit(0)
               }
               
         
       }
       case _ =>{}
    }
  }
  class Node(nodeindex:Int, ID: String,numRequests:Int,master:ActorRef  ) extends Actor {
   val indexID=nodeindex
   val nodeID=ID
   val boss=master
   val routeT=new Array[Array[String]](bits/b)
   val LeftLeaf=new Array[String](L/2)
   val RightLeaf=new Array[String](L/2)
   var Transmitcount=0
   var sumhops=0
   var statemap= HashMap.empty[String,Int] 
   val failconns = HashMap.empty[String,Int]
	  var diestate = 0 
	 val retransmit = 3

   	  def setMode(state:Int,conns:List[(String,Int)]) = {
	    
	    diestate = state
	    if(conns!=null && conns.size>0){
	      conns.foreach( pair => failconns+= {pair._1 -> pair._2})
	    }
	  }
	  
   
   def IDSearch(prefixR:String, Sortnode:Array[String],beginN:Int,endN:Int):String={
     for(m<-beginN until endN){
       if(Sortnode(m).startsWith(prefixR)) {return Sortnode(m)}       
     }
     return null
   }
   def initialize(network:HashMap[String, ActorRef],nodemap:TreeMap[Int,String])={
     val routecol=math.pow(2,b).toInt
     for(i<-0 until routeT.size){
       routeT(i)=new Array[String](routecol)
     }
     val Sortnode=nodemap.values.toArray
     util.Sorting.quickSort(Sortnode)
     val index=Sortnode.indexOf(nodeID) 
     for(i<-0 until L/2){
       
        LeftLeaf(i)={if(index>=L/2-i)Sortnode(index-L/2+i)else Sortnode(0)}
        RightLeaf(i)={if(index<Sortnode.size-i-1)Sortnode(index+i+1)else Sortnode.last}
     }
  
     for(i<-0 until routeT.size){
         var prefixR =nodeID.substring(0, i)
         var digitR=nodeID.substring(i, i+1)
         var IntdigitR=Integer.parseInt(digitR,16)
         for(j<-0 until routecol){
             if(j==IntdigitR){routeT(i)(j)=nodeID}
             else if(j<IntdigitR){routeT(i)(j)=IDSearch(prefixR+Integer.toString(j,16),Sortnode,0,index)}
             else{
               if (index+1<Sortnode.size){routeT(i)(j)=IDSearch(prefixR+Integer.toString(j,16),Sortnode,index+1,Sortnode.size)}
             }          
         }
         
     }
        LeftLeaf.foreach(x => if(x!=nodeID) statemap+={x->0})
	    RightLeaf.foreach(x => if(x!=nodeID) statemap+={x->0})
	   
	    routeT.foreach( x => x.foreach(y => if(y!=nodeID&&y!=null) statemap+={y->0}) )
	    
     self! sendMessage(network,nodemap)
   } 
def alter(k:String, orig:String, curr:String,nodemap:TreeMap[Int,String]):String = {
	    
	    val sortN = nodemap.values.toArray
	    util.Sorting.quickSort(sortN)
	    val index = sortN.indexOf(orig)
	    
	    var result = orig
	    var resindex = index
	    if(index==0 || (k>orig && index<sortN.length-1)) {result=sortN(index+1);resindex=index+1}
		    else { result=sortN(index-1);resindex=index-1 }
		    
	    while(result==curr){
		  resindex += resindex-index
		  if(resindex==sortN.size){resindex=index-1}
		  else if(resindex==0){ resindex = index+1}
		  result = sortN(resindex)
	    }
	    
	    return result
	    
	  }
	  
   
   def getroute(thekey:String):String = { 
           if (thekey==null ||LeftLeaf(0)==null||RightLeaf(0)==null||LeftLeaf.last==null||RightLeaf.last==null){return nodeID}

       var cha=math.abs(Long.parseLong(thekey, 16)-Long.parseLong(nodeID, 16))
       var Mindex= -1
       
       if(thekey>=LeftLeaf(0) && thekey<=nodeID){ 
            for(i<- 0 until LeftLeaf.size){
               val tempcha=math.abs(Long.parseLong(thekey, 16)-Long.parseLong(LeftLeaf(i), 16))
               if (tempcha<=cha){
                      cha=tempcha
                      Mindex=i
                      
                 }
            }
         if (Mindex<0) return nodeID else return LeftLeaf(Mindex)
       }
       else if(thekey > nodeID && thekey <= RightLeaf(RightLeaf.size-1)){ 
           for (i<- 0 until RightLeaf.size){
                val tempcha=math.abs(Long.parseLong(thekey, 16)-Long.parseLong(RightLeaf(i), 16))
                if (tempcha<=cha){
                      cha=tempcha
                      Mindex=i                      
                 } 
           }
           if (Mindex<0) return nodeID else return RightLeaf(Mindex) 
       }
       else{ 
         var mcp=(thekey,nodeID).zipped.takeWhile(Function.tupled(_==_)).map(_._1).mkString
         var lenOfmcp=mcp.length
         var digitR=Integer.parseInt(thekey.substring(lenOfmcp, lenOfmcp+1), 16) 
         if (routeT(lenOfmcp)(digitR)!=null){
             return routeT(lenOfmcp)(digitR)
         }
         else{
           val row=routeT(lenOfmcp) 
           for(i<-0 until row.size){
               val tempcha=if (row(i)==null) cha else math.abs(Long.parseLong(thekey, 16)-Long.parseLong(row(i), 16))
               if (tempcha<cha){
                  cha=tempcha
                  Mindex=i
               }
           }
           if(Mindex>=0){return row(Mindex)}
           else if(thekey<nodeID) return LeftLeaf(0)
           else return RightLeaf.last
         }         
       }     
   }
  def receive ={
    case Init(network,nodemap)=>initialize(network,nodemap)
  
        case fconn(fnetwork,fmymap,wakeup)=>{
      val conleaf = LeftLeaf++RightLeaf
      var id1=nodeID
      val id2 = conleaf((math.random*conleaf.size).toInt)
      val conns1 = List[(String,Int)]((id2,wakeup)); setMode(1, conns1)
      val conns2 = List[(String,Int)]((id1,wakeup)); fnetwork(id2)!setMode(1, conns2)
      master! failconf(id1,id2,1)
      println(" Connection dies temporarily: Node (" + id1 + ")<->N (" + id2 + ")")
    }
    case setMode(a,b)=>setMode(a,b)
    
    case fconnp(fnetwork,fmymap)=>{
      val conleaf = LeftLeaf++RightLeaf
      var idp1=nodeID
      val idp2 = conleaf((math.random*conleaf.size).toInt)
      val connsp1 = List[(String,Int)]((idp2,-1)); setMode(1, connsp1)
      val connsp2 = List[(String,Int)]((idp1,-1)); fnetwork(idp2)!setMode(1, connsp2)
      master! failconf(idp1,idp2,2)
      println("  (" + idp1 + ") <-> (" + idp2 + ")  connection dies permanently")
    }
    case sendMessage(network,nodemap)=>{ 
               for(i<-1 to numRequests) {
               var messagekey=md5((math.random*maxlen).toLong)
               val routenode=getroute(messagekey)
               if (routenode==nodeID){
                 network(routenode)! messageTransmit(i,nodeID,messagekey,"null",0)
               }
               else {network(routenode)! message(i,nodeID,nodeID,messagekey,"",1,network,nodemap)}
          } 
      
    }
      
    case message(j,from,initnode,messagekey,path,hops,network,nodemap)=>{
  
               
	          if(diestate == 2 && (!statemap.contains(from) || statemap(from)>=0)){
	            network(from) ! Failure("Messg",j,nodeID,initnode,messagekey,path,hops,network,nodemap)
	          }
	          else if(diestate>0 && failconns.contains(from) && failconns(from)!=0){
	            network(from) ! Failure("Messg",j,nodeID,initnode,messagekey,path,hops,network,nodemap)
	            failconns +={from->(failconns(from)-1)}	            
	          }
	          else{	              
	              if(diestate>0 && failconns.contains(from) && failconns(from)==0){
		              failconns.remove(from)
		              if(diestate==1 && (failconns.size==0||failconns(from)==0)) { diestate=0 }
		              network(from) ! Wakeup(nodeID)
	              }
	            
	           val routenode=getroute(messagekey)
               if (routenode==nodeID){
                   network(initnode)! messageTransmit(j,initnode,messagekey,path+nodeID,hops)
               }
               else {network(routenode)! message(j,nodeID,initnode,messagekey,path+nodeID+"+",hops+1,network,nodemap)} 

	          }

    }
    case messageTransmit(j,initnode,messagekey,path,hops)=>{
           Transmitcount+=1
           sumhops+=hops
           println("node "+nodeID+" received message "+j+", through the path is "+path+","+hops+" hop(s) took place ")
           if(Transmitcount==numRequests){
               master! finish(indexID,sumhops)
           }
                     
    }
    case "stop"=>System.exit(0)
    case Failure(cat,seq,from,initnode,key,path,hops,network,nodemap) => {
	          
	          if(statemap(from)<retransmit){
	              
	              println( "(" + nodeID + ") temporarily fails to connect with (" + from + ") " )
	            
		          statemap += {from->(statemap(from)+1)}

		          network(from) ! message(seq,nodeID,initnode,key,path+nodeID+"+",hops+1,network,nodemap)
	          }
	          else{
	            var next = alter(key,from,nodeID,nodemap)
	            
	            println( "(" + nodeID + ") permanently fails to connect with (" + from + ") !" +"\n"
	                + "use (" +next + ") to replace " + from  )
	            
	            network(from) ! Contact(seq,nodeID,initnode,key,path,hops,network,nodemap)
	            
	          }
	}
	        
  case Wakeup(from) => {
	          println("N" + indexID + "(" + nodeID + ") find connect with N"  
	                + "(" + from + ") wake up!")
	        }
 case Contact(seq,from,initnode,messagekey,path,hops,network,nodemap) => {
	          if(diestate>0 && failconns.contains(from) && failconns(from)<0){
	            failconns.remove(from) 
	            if(diestate==1 && failconns.size==0) { diestate=0 }
	          }
	          else if(diestate==2){
	              
	            statemap+=(from -> (-1))
	              
	          }
	          val routenode=getroute(messagekey)
               if (routenode==nodeID){
                   network(initnode)! messageTransmit(seq,initnode,messagekey,path+nodeID,hops)
               }
               else {network(routenode)! message(seq,nodeID,initnode,messagekey,path+nodeID+"+",hops+1,network,nodemap)} 
	          	          	          
	        }
    case _=>{}
  } 

  }
  
}