package mat.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import gov.cms.mat.fhir.rest.dto.ConversionResultDto;
import gov.cms.mat.fhir.rest.dto.CqlConversionError;
import gov.cms.mat.fhir.rest.dto.FhirValidationResult;
import gov.cms.mat.fhir.rest.dto.LibraryConversionResults;
import gov.cms.mat.fhir.rest.dto.ValueSetValidationResult;
import mat.dao.clause.MeasureDAO;
import mat.model.clause.Measure;
import mat.shared.DateUtility;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("fhirValidationService")
public class FhirValidationReportService {

    private static final String DATE_FORMAT = "dd-MMM-YYYY";
    private static final String TIME_FORMAT = "hh:mm aa";
    private static final String NO_MEASURE_FOUND_ERROR = "The measure with that measure id does not exist.";
    private static final String CONVERSION_SERVICE_ERROR = "An error occurred while validating the FHIR conversion. Please try again later.";
    private static final Log logger = LogFactory.getLog(FhirValidationReportService.class);

    private Configuration freemarkerConfiguration;

    private MeasureDAO measureDAO;

    @Value("${mat.measure.current.release.version}")
    private String currentMatVersion;

    public FhirValidationReportService(Configuration freemarkerConfiguration, MeasureDAO measureDAO) {
        this.freemarkerConfiguration = freemarkerConfiguration;
        this.measureDAO = measureDAO;
    }

    /**
     * This method calls the QDM to FHIR conversion validation micro service for a given measure
     * and generates the FHIR conversion validation report for measure and it's cql libraries & value sets,
     *
     * @param measureId- Measure id for which validation report is requested
     * @return HTML string report
     * @throws IOException
     * @throws TemplateException
     */
    public String getFhirConversionReportForMeasure(String measureId) throws IOException, TemplateException {
        ConversionResultDto conversionResult = null;
        Measure measure = measureDAO.find(measureId);
        if (measure != null) {
            conversionResult = validateFhirConversion(measureId);
        }
        return generateValidationReport(conversionResult, measure);
    }

    /**
     * Calls the QDM to FHIR conversion validation service and returns the Conversion Results
     *
     * @return an instance of FHIR ConversionResultDto
     * @throws IOException
     */
    private ConversionResultDto validateFhirConversion(String measureId) throws IOException {
        if (measureId == null) {
            return null;
        }

        URL path = FhirValidationReportService.class.getResource("report.json");
        logger.info("Calling FHIR conversion validation service for measure: "+ measureId);
        //TODO: Replace following line with actual call to FHIR conversion service once it is available on AWS
        // and delete report.json mock data
        return new ObjectMapper()
                .readValue(new File(path.getFile()),
                        ConversionResultDto.class);
    }

    /**
     * Generates the validation report from FHIR conversion validation result object
     *
     * @return HTML String representation of a FHIR conversion validation report for given measure
     * @throws IOException
     * @throws TemplateException
     */
    private String generateValidationReport(ConversionResultDto conversionResultDto, Measure measure) throws IOException, TemplateException {
        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("matVersion", currentMatVersion);

        if (conversionResultDto != null && measure != null) {
            //Measure FHIR validation errors
            List<FhirValidationResult> measureFhirValidationErrors = conversionResultDto.
                    getMeasureConversionResults().
                    getMeasureFhirValidationErrors();

            //Library FHIR validation errors
            LibraryConversionResults libraryConversionResults = conversionResultDto.getLibraryConversionResults();
            List<FhirValidationResult> libraryFhirValidationErrors = libraryConversionResults.getLibraryFhirValidationErrors();

            //CQL conversion errors
            List<CqlConversionError> cqlConversionErrors = libraryConversionResults.
                    getCqlConversionResult().
                    getCqlConversionErrors();

            //ValueSet FHIR validation errors
            List<ValueSetValidationResult> valueSetFhirValidationErrors = conversionResultDto.
                    getValueSetConversionResults().
                    getValueSetFhirValidationErrors();

            Instant instant = Instant.parse(conversionResultDto.getModified());
            paramsMap.put("runDate", DateUtility.formatInstant(instant, DATE_FORMAT));
            paramsMap.put("runTime", DateUtility.formatInstant(instant, TIME_FORMAT));
            paramsMap.put("measureName", measure.getDescription());
            paramsMap.put("measureVersion", measure.getVersion());
            paramsMap.put("modelType", measure.getMeasureModel());

            paramsMap.put("measureFhirValidationErrors", measureFhirValidationErrors);
            paramsMap.put("libraryFhirValidationErrors", libraryFhirValidationErrors);
            paramsMap.put("cqlConversionErrors", cqlConversionErrors);
            paramsMap.put("valueSetFhirValidationErrors", valueSetFhirValidationErrors);
        } else if (measure == null) {
            paramsMap.put("noMeasureFoundError", NO_MEASURE_FOUND_ERROR);
        } else {
            paramsMap.put("conversionServiceError", CONVERSION_SERVICE_ERROR);
        }

        return FreeMarkerTemplateUtils.processTemplateIntoString(
                freemarkerConfiguration.getTemplate("fhirvalidationreport/fhir_validation_report.ftl"),
                paramsMap);
    }
}
