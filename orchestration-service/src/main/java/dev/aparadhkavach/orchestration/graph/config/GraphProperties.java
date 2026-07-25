package dev.aparadhkavach.orchestration.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Graph Intelligence network knobs (K2 / A9). Distinct from {@code traversal-depth} which remains
 * the F3 conversational default (often 3); network API hard-caps at {@code network-max-depth}.
 */
@Component
@ConfigurationProperties(prefix = "aparadhkavach.graph")
public class GraphProperties {

  /** Conversational / F3 default (Section 6.5); unused by the network endpoint. */
  private int traversalDepth = 3;

  private int networkDefaultDepth = 1;
  private int networkMaxDepth = 2;
  private int networkMaxNodes = 50;

  public int getTraversalDepth() {
    return traversalDepth;
  }

  public void setTraversalDepth(int traversalDepth) {
    this.traversalDepth = traversalDepth;
  }

  public int getNetworkDefaultDepth() {
    return networkDefaultDepth;
  }

  public void setNetworkDefaultDepth(int networkDefaultDepth) {
    this.networkDefaultDepth = networkDefaultDepth;
  }

  public int getNetworkMaxDepth() {
    return networkMaxDepth;
  }

  public void setNetworkMaxDepth(int networkMaxDepth) {
    this.networkMaxDepth = networkMaxDepth;
  }

  public int getNetworkMaxNodes() {
    return networkMaxNodes;
  }

  public void setNetworkMaxNodes(int networkMaxNodes) {
    this.networkMaxNodes = networkMaxNodes;
  }
}
