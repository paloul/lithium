package akka.cluster.sbr.scenarios

import akka.cluster.ClusterEvent.{MemberUp, UnreachableMember}
import akka.cluster.Member
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.WorldView
import akka.cluster.sbr.scenarios.Scenario.splitCluster
import cats.data.{NonEmptyList, NonEmptySet}
import org.scalacheck.Arbitrary
import org.scalacheck.Gen.listOf
import akka.cluster.sbr.implicits._

final case class UpDisseminationScenario(worldViews: NonEmptyList[WorldView])

object UpDisseminationScenario {
  implicit val arbUpDisseminationScenario: Arbitrary[UpDisseminationScenario] = Arbitrary {

    /**
     * Yields a [[WorldView]] that based on `worldView`
     * that sees all the nodes not in the `partition`
     * as unreachable and seems some members up that others
     * do not see.
     */
    def divergeWorldView(worldView: WorldView,
                         allNodes: NonEmptySet[Member],
                         partition: NonEmptySet[Member]): Arbitrary[WorldView] = Arbitrary {
      listOf(arbWeaklyUpMember.arbitrary)
        .map(_.foldLeft(worldView) {
          case (worldView, member) =>
            worldView.memberEvent(MemberUp(member.copyUp(Integer.MAX_VALUE)))
        })
        .map { worldView =>
          val otherNodes = allNodes -- partition

          otherNodes.foldLeft[WorldView](worldView) {
            case (worldView, node) => worldView.reachabilityEvent(UnreachableMember(node))
          }
        }
    }

    for {
      healthyWorldView <- arbUpNumberConsistentWorldView.arbitrary

      allNodes = NonEmptySet
        .fromSetUnsafe(healthyWorldView.allNodes) // HealthyWorldView has at least one node and all are reachable

      // Split the allNodes in `nSubCluster`.
      partitions <- splitCluster(allNodes)

      // Each sub-allNodes sees the other nodes as unreachable.

      divergedWorldViews <- partitions.traverse(divergeWorldView(healthyWorldView, allNodes, _)).arbitrary
    } yield UpDisseminationScenario(divergedWorldViews)
  }

}
