```{viz}
digraph handler {
    edge[penwidth=.5, color=slategrey]
    subgraph cluster_core {
      style = dashed;
      penwidth = .5;
      node[shape=plaintext]
      queue[label="MessageQueue"]
      msg[label="Message"]
      local[label="ThreadLocal"]
      looper[label="Looper", shape=box, color=red]

      looper -> queue[weight=100]
      looper -> local[weight=100]
      queue -> msg[dir=back]
    }
    node[shape=plaintext]
    cur[label="CurrentThread"]
    handler[label="Handler"]
    cur -> looper
    handler -> looper
    handler -> msg[dir=back, label="target", fontcolor=purple]
}
```

上图描述的是Android消息机制的基本组成结构，关于这个结构图有如下的几点内容

1. `Looper`是这个消息机制中最为关键的一环，这个类为`Handler`提供在不同线程中向目标线程发送消息的功能，同时进行着消息遍历不断从消息队列中获取最新消息的工作。

2. `Looper`通过`ThreadLocal`的方式实现，保证每个实现消息循环的线程都拥有自己独立的`Looper`对象。

3. `MessageQueue`使用一个单链表为`Looper`提供消息队列的支持，这种支持包括将插入的消息按照时间顺序不断的插入队列中；并且在一个循环中不断的输出可以处理的消息直到当前的消息循环退出。

4. `Message`通过`target`属性可以在该消息需要进行处理的时候找到最初发出这个消息的`Handler`，调用其对应的处理函数处理这个消息。

5. `CurrentThread`只是提供了处理消息的线程，对于整个消息的流程完全被`Looper`透明了出来，一脸懵逼。

# 关键类分析

## Message

`Message`是整个消息体系里面信息的载体，在这个类的源代码中提供了几个预留的属性作为信息载体槽。整个类设计大概有下面的这几个关键点：

1. 预留信息槽作为载体

2. 提供回收机制，通过单链表维护一个缓冲池

3. 使用标志位标识消息当前处于怎样的状态

4. 该类提供了一个用于标识该消息是否为异步消息的字段，这个字段的作用在于，如果消息队列中存在`Barrier`对整个队列实行了阻塞的操作，这种情况下执行时间点在这个`Barrier`之后的同步消息（默认是同步消息）将无法得到执行直到这个`Barrier`被移除之后，但是这种类型的阻塞操作对异步消息不会产生影响，依然能够得到正常的执行。

## MessageQueue

1. `MessageQueue`的职责是维护一个`Message`单链表，提供提供针对这个链表的增删改查的接口操作。

2. `MessageQueue`通过`next`向上层提供可执行消息的时候，可以通过添加`Barrier`的方式阻塞在这个消息之后的同步消息执行，但是不影响异步消息

3. 在开启一个消息循环之后会在当前的线程一直执行而导致当前线程阻塞，所以在确认这个消息队列已经完成了它对应的职责之后退出当前的消息队列，但是要注意在调用`MessageQueue`构造函数的时候传入的`allowQuit`。同时还要意识到另外的有点，这个队列一旦被退出之后，就不能够再次恢复的操作。

4. `MessageQueue`中提供了一个类`IdelHandler`，并且维护了对应的一个队列。这个对象提供的操作会在当前线程或者说`next`函数空闲的时候去执行。

## Looper

1. `Looper`通过`ThreadLocal`的实现方式依附到目标执行线程中，为消息处理提供对应的运行线程和场所

2. `Looper.prepared()`执行线程的依附操作，`Looper.loop()`开始消息队列的循环从而不断的从消息队列中取出可以执行的消息

3. 拿到消息之后，会在`Looper.loop()`中直接执行当前的消息处理过程


## Handler

1. `Handler`在创建的时候会拿到当前线程对用的`Looper/MessageQueue`等属性，通过这些属性封装出消息对应的操作接口

2. `Handler`可以通过某个接口的调用去清空当前的消息队列，但是注意到消息队列和`handler`并不存在一一对应的关系，所以在执行清空操作的时候一定要注意是否存在额外的影响

3. `Handler`消息处理流程存在一定的优先级，首先是`Message.r`这个是常用的`post runnable`的方式；然后是`Handler.callback`也一样具备处理消息的内容，如果在这个步骤执行的结果是true就会直接退出消息的执行流程；最后才会进入到`handler.handleMessage`的处理过程

## 关键类结构设计

```{viz}

digraph KeyClassesStructure {
  node[shape = plaintext];
  edge[color = slategrey, penwidth = .5];
  rankdir = LR;
  subgraph cluster_msg_queue {
    label = "封装消息队列的维护"
    style = dashed;
    color = purple;
    penwidth = .5;
    node[shape = plaintext];
    edge[color = slategrey, penwidth = .5];
    Message -> MessageQueue -> Looper;
  }  

  Looper -> LooperThread [label = "ThreadLocal"];
  MessageQueue -> Handler;
  Looper -> Handler [label = "client interface"];
}

```
