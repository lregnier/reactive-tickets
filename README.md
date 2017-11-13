# Reactive Tickets

The aim of this project is to show how through a series of simple steps a basic Akka application can be refactored into a reactive system which truly complies with all of the principles of the [Reactive Manifesto]: Responsive, Resilient, Elastic and Message-driven. 

Originally the code for this project was conceived as a hands-on introduction to [Akka Cluster] presented in this [talk]. For further information, please refer to the slides.

## The way to Reactiveness:
You will find three PRs which are meant to remain open in order to quickly compare the changes from one step to the other.

As a precondition we can establish that the application is already **message-driven** since it relies on asynchronous message-passing as its foundation as it is mostly backed by Akka. 

### Step 1: Add Akka Cluster features
The first goal is to be able to scale out the application which will be the first step to make it **responsive** and pave the way to rest of the reactive principles.

The first issue that we're going to find while doing so is that `TicketSellerSupervisor` and all of the `TicketSeller` instances are stateful actors. This means that when running more than one node, we're going to end up with multiple incarnations of the same actor which will lead to inconsistency issues. In order to avoid this problem, we need to add Akka Cluster and Cluster Singleton support. After adding the proper dependencies and configurations, we need to change the initiation of `TicketSellerSupervisor` from a regular actor to a singleton actor. This is going to guarantee that we only have one instance of `TicketSellerSupervisor` along the cluster. The same will happen for all `TicketSeller` instances as they are children from `TicketSellerSupervisor`. The actual implementation of `TicketSellerSupervisor` and `TicketSeller` remains the same.

### Step 2: Add Akka Cluster Sharding features
At this step, we want to add to the application the ability of distributing its resources evenly along the cluster, i.e. we want it to be **elastic**. 

One of the main problems with the current code at this stage is that all of our stateful actors are now living in the same node. This represents both a bottleneck and a single point of failure. In order to face this issue, we need to introduce Cluster Sharding. Upon adding the corresponding dependencies and updating the configuration accordingly, we only need to replace our `TicketSellerSupervisor` by a proper ShardRegion actor. The Sharding extension will take care of distributing all of the `TicketSeller` evenly among all of the nodes, no need to write any further code. Again the actual implementation of `TicketSeller` remains the same.

### Step 3: Add Akka Persistence features
Last, we want our system to stay responsive in the face of failure, that is, to be **resilient**.

At this point, if one of the nodes of the cluster crashes, although the actors that were alive on it are going to be automatically created again in some other running node through Sharding, their previous state is going to be lost. To solve this issue, we need to add Akka Persistence. Upon adding the proper dependencies and configurations and slightly updating the `TicketSeller` implementation, no state is ever going to be lost.

I you've made it up to this point, it means that you have gone through all the steps into building a real reactive application using Akka as its backbone.

[Reactive Manifesto]: <https://www.reactivemanifesto.org/>
[Akka Cluster]: <https://doc.akka.io/docs/akka/2.5/scala/common/cluster.html>
[talk]: <http://slides.com/leonardoregnier/deck-da854491-3768-4722-b6f8-c2589194798f#/>