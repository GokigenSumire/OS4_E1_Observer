package personal.wallpaper.os4.e1.lockinput;

import java.util.ArrayDeque;
import personal.wallpaper.protocol.lock.LockProtocol;

/** 只保留当前锁屏场景的逻辑动作；不保存触摸或已签发的图片请求。 */
final class PriorityGestureQueue {
    static final int CAPACITY=32;
    static final long MAX_WAIT_MILLIS=10_000L;
    static final class Owner {
        final Object channel,root,window;final long actorEpoch;final String scene;
        Owner(Object channel,Object root,long actorEpoch,String scene){this(channel,root,actorEpoch,scene,root);}
        Owner(Object channel,Object root,long actorEpoch,String scene,Object window){
            if(channel==null||root==null||window==null||actorEpoch<=0||!LockProtocol.token(scene))throw new IllegalArgumentException("INVALID_PRIORITY_OWNER");
            this.channel=channel;this.root=root;this.actorEpoch=actorEpoch;this.scene=scene;this.window=window;
        }
        boolean same(Owner other){return other!=null&&channel==other.channel&&root==other.root&&window==other.window&&actorEpoch==other.actorEpoch&&scene.equals(other.scene);}
    }
    static final class Item {
        final Owner owner;final int action;final long created;
        private Item(Owner owner,int action,long created){this.owner=owner;this.action=action;this.created=created;}
    }
    private final ArrayDeque<Item> items=new ArrayDeque<>();
    private Owner owner;
    synchronized int bind(Owner next){if(next==null)throw new IllegalArgumentException();if(next.same(owner))return 0;int dropped=items.size();items.clear();owner=next;return dropped;}
    synchronized boolean offer(Owner expected,int action,long now){
        if(owner==null||!owner.same(expected)||!LockProtocol.action(action)||now<0||items.size()>=CAPACITY)return false;
        items.addLast(new Item(owner,action,now));return true;
    }
    synchronized int expire(long now){int dropped=0;while(!items.isEmpty()){Item item=items.peekFirst();if(now>=item.created&&now-item.created<=MAX_WAIT_MILLIS)break;items.removeFirst();dropped++;}return dropped;}
    synchronized Item peek(){return items.peekFirst();}
    synchronized boolean acknowledge(Item item){if(item==null||items.peekFirst()!=item)return false;items.removeFirst();return true;}
    synchronized int clear(){int dropped=items.size();items.clear();owner=null;return dropped;}
    synchronized int clearFor(Object channel){return owner!=null&&owner.channel==channel?clear():0;}
    synchronized int size(){return items.size();}
}
