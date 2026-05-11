# Moodle Facade

### Setup
1. Run MariaDB and Moodle application docker images
2. Open Moodle page (https://localhost:8080) and login with admin credentials
3. Follow the steps in 'Site Administiation' -> 'Server' -> 'Web Services' -> 'Overview' to set up a service
4. Enter the moodle token into docker-compose.yml moodle-facade service environment variable
5. Set the assignmentId in docker-compose.yml evaluator-mock service environment variable


### Endpoints


- Pull a list of submissions with file urls
````
@GetMapping("/pull")
public ResponseEntity<?> listForSolution(@RequestParam long assignmentId,
                                         HttpServletRequest req)
````

- Pull a specific submitted file
````
@GetMapping("/submissions/{submissionId}/file")
public ResponseEntity<?> fetchFile(
        @PathVariable long submissionId,
        @RequestParam long assignmentId,
        @RequestParam(name = "index", defaultValue = "0") int index)
````

- Post a grade and a feedback to Moodle

````
    @PostMapping("/results")
    public String postResults(@RequestBody Map<String, Object> result)
````