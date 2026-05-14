package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.LocationSearchResultDto;
import hr.kronos.backend.locations.LocationSearchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
  private final LocationSearchService locationSearchService;

  public LocationController(LocationSearchService locationSearchService) {
    this.locationSearchService = locationSearchService;
  }

  @GetMapping("/search")
  public List<LocationSearchResultDto> searchLocations(
      @RequestParam String query,
      @RequestParam(defaultValue = "hr") String locale,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng) {
    return locationSearchService.search(query, locale, limit, lat, lng);
  }
}
