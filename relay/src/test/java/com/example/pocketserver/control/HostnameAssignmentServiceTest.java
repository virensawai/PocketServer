package com.example.pocketserver.control;

import com.example.pocketserver.control.model.Deployment;
import com.example.pocketserver.control.repository.ControlPlaneRepository;
import com.example.pocketserver.control.service.HostnameAssignmentService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HostnameAssignmentServiceTest {

    private ControlPlaneRepository repository;
    private HostnameAssignmentService service;

    @Before
    public void setUp() {
        repository = new ControlPlaneRepository();
        service = new HostnameAssignmentService(repository);
    }

    @Test
    public void testSanitizeHostname() {
        assertEquals("my-portfolio", HostnameAssignmentService.sanitize("My-Portfolio"));
        assertEquals("myportfolio", HostnameAssignmentService.sanitize("My Portfolio! @#$"));
        assertEquals("site-1", HostnameAssignmentService.sanitize("--site-1--"));
    }

    @Test
    public void testAssignAvailableHostname() {
        String assigned = service.assignHostname("cool-site", "Cool Project", "dep_1");
        assertEquals("cool-site", assigned);
    }

    @Test
    public void testAssignCollisionResolvesUniqueSuffix() {
        // Register an existing deployment with 'cool-site'
        Deployment existing = new Deployment(
                "dep_0", "proj_0", "dev_0", "usr_0", 1, "PUBLIC",
                "cool-site", "LIVE", true, System.currentTimeMillis()
        );
        repository.saveDeployment(existing);

        String assigned = service.assignHostname("cool-site", "Cool Project", "dep_1");
        assertNotNull(assigned);
        assertFalse("Assigned hostname should not collide", "cool-site".equals(assigned));
        assertTrue("Assigned hostname should start with sanitized prefix", assigned.startsWith("cool-site-"));
    }

    @Test
    public void testFallbackWhenRequestedIsEmpty() {
        String assigned = service.assignHostname(null, "React Vite App", "dep_2");
        assertNotNull(assigned);
        assertTrue(assigned.startsWith("reactviteapp-"));
    }
}
