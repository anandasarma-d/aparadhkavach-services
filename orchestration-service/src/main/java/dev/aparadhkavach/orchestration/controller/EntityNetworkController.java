package dev.aparadhkavach.orchestration.controller;

import dev.aparadhkavach.orchestration.dto.EntityNetworkResource;
import dev.aparadhkavach.orchestration.dto.NetworkEdgeResource;
import dev.aparadhkavach.orchestration.dto.NetworkNodeResource;
import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.service.EntityNetworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Criminal network discovery (KSP #2 / Design & Schema §6.7). Read-only neighborhood — no community
 * detection, no Claude summary in this slice (Auto/17 A9).
 */
@RestController
@RequestMapping("/v1/entities")
public class EntityNetworkController {

  private static final Logger log = LoggerFactory.getLogger(EntityNetworkController.class);

  private final EntityNetworkService entityNetworkService;

  public EntityNetworkController(EntityNetworkService entityNetworkService) {
    this.entityNetworkService = entityNetworkService;
  }

  @GetMapping("/{entityId}/network")
  public EntityNetworkResource getNetwork(
      @PathVariable String entityId, @RequestParam(required = false) Integer depth) {
    // Entry log: a Catalyst 408 with no line here means the request never reached the handler.
    log.info("network request entityId={} depth={}", entityId, depth);
    return toResource(entityNetworkService.getNetwork(entityId, depth));
  }

  private static EntityNetworkResource toResource(EntityNetwork network) {
    return new EntityNetworkResource(
        network.entityId(),
        network.entityLabel(),
        network.depth(),
        network.nodes().stream()
            .map(n -> new NetworkNodeResource(n.id(), n.type(), n.label()))
            .toList(),
        network.edges().stream()
            .map(e -> new NetworkEdgeResource(e.from(), e.to(), e.type()))
            .toList(),
        network.truncated());
  }
}
