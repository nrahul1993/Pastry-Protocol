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

object pastryProj {
    
  //System.setOut(new PrintStream(new FileOutputStream("PastryNetworkFlow.txt")));
  val system =ActorSystem("ThePastry")
  case class message(index:Int,nodeID:String,messagekey:String,path:String,hopnum:Int,NetWork:HashMap[String, ActorRef])
  case class messageTransmit(index:Int,nodeID:String,messagekey:String,path:String,hopnum:Int)
  case class finish(indexID:Int,sumhops:Int)
  case class Init(NetWork:HashMap[String, ActorRef],mymap:TreeMap[Int,String])
  case class sendMessage(network:HashMap[String, ActorRef])
  val bits=32
  val b=4
  val L=math.pow(2,b).toInt  
  val maxlen=math.pow(2,bits).toLong

  def main(args: Array[String]): Unit = {
    if(args.length < 2){
      println("Need two inputs!")
      return
    }
    
    val numNodes = args(0).toInt
    val numRequests = args(1).toInt
     var master = system.actorOf(Props(new Master(numNodes,numRequests)))  
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
  class Master(numNodes: Int, numRequests:Int) extends Actor with ActorLogging {
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
        
    }
   def receive = {
       case finish(nodeindex,nodehop) =>{
               totalhops+=nodehop
               count+=1
               println(count)
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
     self! sendMessage(network)
   } 
   def getroute(thekey:String):String = { 
      if (thekey==null ||LeftLeaf(0)==null||RightLeaf(0)==null||LeftLeaf.last==null||RightLeaf.last==null){return nodeID}
       var cha=math.abs(Long.parseLong(thekey, 16)-Long.parseLong(nodeID, 16))
       var Mindex= -1
       if(Long.parseLong(thekey, 16)>=Long.parseLong(LeftLeaf(0), 16) && Long.parseLong(thekey, 16)<=Long.parseLong(nodeID, 16)){ 
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
              
    case sendMessage(network)=>{ 
               for(i<-1 to numRequests) {
               var messagekey=md5((math.random*maxlen).toLong)
               val routenode=getroute(messagekey)
               if (routenode==nodeID){
                 network(routenode)! messageTransmit(i,nodeID,messagekey,"null",0)
               }
               else {network(routenode)! message(i,nodeID,messagekey,"",1,network)}
          } 
      
    }
      
    case message(j,initnode,messagekey,path,hops,network)=>{
               val routenode=getroute(messagekey)
               if (routenode==nodeID){
                   network(initnode)! messageTransmit(j,initnode,messagekey,path+nodeID,hops)
               }
               else {network(routenode)! message(j,initnode,messagekey,path+nodeID+"+",hops+1,network)}               

    }
    case messageTransmit(j,initnode,messagekey,path,hops)=>{
           Transmitcount+=1
           sumhops+=hops
           println("node "+nodeID+" received message "+j+", through the path is "+path+","+hops+" hop(s) took place ")
           if(Transmitcount==numRequests){
               master! finish(indexID,sumhops)
           }
                     
    }
    case "stop"=> System.exit(0)
    case _=>{}
  } 

 }


}