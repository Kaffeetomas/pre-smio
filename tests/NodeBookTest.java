package net.miotest.client;
public final class NodeBookTest {
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 static NodeBook.Node node(String name,String host,int port){return NodeBook.Node.create(name,host,port,"one.one.one.one",443,"test-secret-not-real");}
 static void rejects(NodeBook.Node n){try{n.validate();throw new AssertionError("accepted invalid node");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception {
  NodeBook b=new NodeBook();NodeBook.Node a=node("First VPS","203.0.113.10",8443),c=node("Second VPS","example.org",443);b.put(a);b.put(c);check(b.nodes().size()==2,"multiple nodes");check(b.selected().id.equals(a.id),"first selection");b.select(c.id);
  NodeBook restored=NodeBook.decode(b.encode());check(restored.nodes().size()==2,"node count roundtrip");check(restored.selected().id.equals(c.id),"selected node roundtrip");check(restored.get(a.id).password.equals(a.password),"password roundtrip");
  NodeBook.Node edited=node("Changed","new.example.org",9443);edited.id=c.id;edited.revision=c.revision+1;restored.put(edited);check(restored.nodes().size()==2,"edit duplicates node");check(restored.selected().server.equals("new.example.org"),"edit selected node");
  restored.remove(c.id);check(restored.selected().id.equals(a.id),"delete selected fallback");restored.remove(a.id);check(restored.selected()==null,"delete last selection");check(NodeBook.decode(restored.encode()).nodes().isEmpty(),"empty collection persisted");
  rejects(node("","example.org",443));rejects(node("Invalid","https://example.org",443));rejects(node("Invalid","example.org:443",443));rejects(node("Invalid","1.1.1.1:443",443));rejects(node("Invalid","example.org",0));rejects(node("Invalid","example.org",65536));
  NodeBook.Node ipv6=node("IPv6","[2001:db8::1]",8443);ipv6.validate();check(ipv6.address().equals("[2001:db8::1]:8443"),"IPv6 normalization");
  try{b.select("missing");throw new AssertionError("missing selection accepted");}catch(IllegalArgumentException expected){}
  System.out.println("NodeBook tests passed: CRUD, selected-node persistence, credentials, empty state, invalid input, IPv6.");
 }
}
