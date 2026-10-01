package org.brite.sample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sample")
public class SampleController {

    public final SampleService sampleService;

    @Autowired
    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @GetMapping("/spl")
    public ResponseEntity<Integer> sample(@RequestParam String item) {
        Integer itemNumber = sampleService.sample(item);
        if (itemNumber == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(itemNumber);
    }

}
