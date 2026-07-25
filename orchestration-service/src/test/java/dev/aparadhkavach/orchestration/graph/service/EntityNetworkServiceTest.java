package dev.aparadhkavach.orchestration.graph.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aparadhkavach.commons.exception.ResourceNotFoundException;
import dev.aparadhkavach.commons.exception.ValidationException;
import dev.aparadhkavach.orchestration.graph.config.GraphProperties;
import dev.aparadhkavach.orchestration.graph.model.EntityNetwork;
import dev.aparadhkavach.orchestration.graph.model.NetworkEdge;
import dev.aparadhkavach.orchestration.graph.model.NetworkNode;
import dev.aparadhkavach.orchestration.graph.repository.EntityNetworkRepository;
import dev.aparadhkavach.orchestration.graph.repository.EntityNetworkRepository.NetworkBundle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityNetworkServiceTest {

  @Mock private EntityNetworkRepository repository;

  private GraphProperties properties;
  private EntityNetworkService service;

  @BeforeEach
  void setUp() {
    properties = new GraphProperties();
    properties.setNetworkDefaultDepth(1);
    properties.setNetworkMaxDepth(2);
    properties.setNetworkMaxNodes(50);
    service = new EntityNetworkService(repository, properties);
  }

  @Test
  void clampsDepthToMaxTwo() {
    NetworkNode start = new NetworkNode("ACC-00124", "Accused", "Demo");
    when(repository.fetchNetwork(eq("ACC-00124"), eq(2), anyInt()))
        .thenReturn(new NetworkBundle(start, List.of(start), List.of()));

    EntityNetwork network = service.getNetwork("ACC-00124", 9);

    assertEquals(2, network.depth());
    verify(repository).fetchNetwork(eq("ACC-00124"), eq(2), anyInt());
  }

  @Test
  void defaultsDepthWhenNull() {
    NetworkNode start = new NetworkNode("ACC-00124", "Accused", "Demo");
    when(repository.fetchNetwork(eq("ACC-00124"), eq(1), anyInt()))
        .thenReturn(new NetworkBundle(start, List.of(start), List.of()));

    assertEquals(1, service.getNetwork("ACC-00124", null).depth());
  }

  @Test
  void truncatesWhenOverMaxNodes() {
    properties.setNetworkMaxNodes(3);
    NetworkNode start = new NetworkNode("ACC-00124", "Accused", "Demo");
    List<NetworkNode> many = new ArrayList<>();
    many.add(start);
    List<NetworkEdge> edges = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      NetworkNode fir = new NetworkNode("FIR-" + i, "FIR", "FIR-" + i);
      many.add(fir);
      edges.add(new NetworkEdge("ACC-00124", fir.id(), "ACCUSED_IN"));
    }
    when(repository.fetchNetwork(eq("ACC-00124"), eq(1), anyInt()))
        .thenReturn(new NetworkBundle(start, many, edges));

    EntityNetwork network = service.getNetwork("ACC-00124", 1);

    assertTrue(network.truncated());
    assertEquals(3, network.nodes().size());
    assertEquals("ACC-00124", network.nodes().get(0).id());
    assertTrue(
        network.edges().stream()
            .allMatch(
                e ->
                    network.nodes().stream().anyMatch(n -> n.id().equals(e.from()))
                        && network.nodes().stream().anyMatch(n -> n.id().equals(e.to()))));
  }

  @Test
  void returnsStartAloneWhenNoEdges() {
    NetworkNode start = new NetworkNode("ACC-00124", "Accused", "Demo");
    when(repository.fetchNetwork(eq("ACC-00124"), eq(1), anyInt()))
        .thenReturn(new NetworkBundle(start, List.of(start), List.of()));

    EntityNetwork network = service.getNetwork("ACC-00124", 1);

    assertFalse(network.truncated());
    assertEquals(1, network.nodes().size());
    assertEquals("Demo", network.entityLabel());
    assertTrue(network.edges().isEmpty());
  }

  @Test
  void rejectsBlankEntityId() {
    assertThrows(ValidationException.class, () -> service.getNetwork("  ", 1));
  }

  @Test
  void propagatesNotFound() {
    when(repository.fetchNetwork(eq("ACC-missing"), eq(1), anyInt()))
        .thenThrow(new ResourceNotFoundException("No graph entity for entityId=ACC-missing"));

    assertThrows(ResourceNotFoundException.class, () -> service.getNetwork("ACC-missing", 1));
  }
}
