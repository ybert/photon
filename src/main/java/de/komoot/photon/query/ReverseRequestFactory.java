package de.komoot.photon.query;

import com.vividsolutions.jts.geom.Point;

import de.komoot.photon.searcher.TagFilter;
import spark.QueryParamsMap;
import spark.Request;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author svantulden
 */
public class ReverseRequestFactory {
    private final RequestLanguageResolver languageResolver;
    private static final LocationParamConverter mandatoryLocationParamConverter = new LocationParamConverter(true);
    private final LayerParamValidator layerParamValidator;

    private static final HashSet<String> REQUEST_QUERY_PARAMS = new HashSet<>(Arrays.asList("lang", "lon", "lat", "radius",
            "query_string_filter", "distance_sort", "limit", "layer", "osm_tag", "debug"));

    public ReverseRequestFactory(List<String> supportedLanguages, String defaultLanguage) {
        this.languageResolver = new RequestLanguageResolver(supportedLanguages, defaultLanguage);
        this.layerParamValidator = new LayerParamValidator();
    }

    public ReverseRequest create(Request webRequest) throws BadRequestException {
        for (String queryParam : webRequest.queryParams()) {
            if (!REQUEST_QUERY_PARAMS.contains(queryParam))
                throw new BadRequestException(400, "unknown query parameter '" + queryParam + "'.  Allowed parameters are: " + REQUEST_QUERY_PARAMS);
        }

        String language = languageResolver.resolveRequestedLanguage(webRequest);

        Point location = mandatoryLocationParamConverter.apply(webRequest);

        double radius = 1d;
        String radiusParam = webRequest.queryParams("radius");
        if (radiusParam != null) {
            try {
                radius = Double.parseDouble(radiusParam);
            } catch (Exception nfe) {
                throw new BadRequestException(400, "invalid search term 'radius', expected a number.");
            }
            if (radius <= 0) {
                throw new BadRequestException(400, "invalid search term 'radius', expected a strictly positive number.");
            } else {
                // limit search radius to 5000km
                radius = Math.min(radius, 5000d);
            }
        }

        boolean locationDistanceSort;
        try {
            locationDistanceSort = Boolean.parseBoolean(webRequest.queryParamOrDefault("distance_sort", "true"));
        } catch (Exception nfe) {
            throw new BadRequestException(400, "invalid parameter 'distance_sort', can only be true or false");
        }

        int limit = 1;
        String limitParam = webRequest.queryParams("limit");
        if (limitParam != null) {
            try {
                limit = Integer.parseInt(limitParam);
            } catch (Exception nfe) {
                throw new BadRequestException(400, "invalid search term 'limit', expected an integer.");
            }
            if (limit <= 0) {
                throw new BadRequestException(400, "invalid search term 'limit', expected a strictly positive integer.");
            } else {
                // limit number of results to 50
                limit = Math.min(limit, 50);
            }
        }

        boolean enableDebug = webRequest.queryParams("debug") != null;

        Set<String> layerFilter = new HashSet<>();
        QueryParamsMap layerFiltersQueryMap = webRequest.queryMap("layer");
        if (layerFiltersQueryMap.hasValue()) {
            layerFilter = layerParamValidator.validate(layerFiltersQueryMap.values());
        }

        String queryStringFilter = webRequest.queryParams("query_string_filter");
        ReverseRequest request = new ReverseRequest(location, language, radius, queryStringFilter, limit, locationDistanceSort, layerFilter, enableDebug);

        QueryParamsMap tagFiltersQueryMap = webRequest.queryMap("osm_tag");
        if (tagFiltersQueryMap.hasValue()) {
            for (String filter : tagFiltersQueryMap.values()) {
                TagFilter tagFilter = TagFilter.buildOsmTagFilter(filter);
                if (tagFilter == null) {
                    throw new BadRequestException(400, String.format("Invalid parameter 'osm_tag=%s': bad syntax for tag filter.", filter));
                }
                request.addOsmTagFilter(TagFilter.buildOsmTagFilter(filter));
            }
        }

        return request;
    }
}
