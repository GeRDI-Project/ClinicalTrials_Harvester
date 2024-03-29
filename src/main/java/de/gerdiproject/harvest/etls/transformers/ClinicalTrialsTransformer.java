/**
 * Copyright © 2019 Komal Ahir (http://www.gerdi-project.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gerdiproject.harvest.etls.transformers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import de.gerdiproject.harvest.clinicaltrials.constants.ClinicalTrialsConstants;
import de.gerdiproject.harvest.clinicaltrials.constants.ClinicalTrialsUrlConstants;
import de.gerdiproject.harvest.etls.AbstractETL;
import de.gerdiproject.harvest.etls.extractors.ClinicalTrialsVO;
import de.gerdiproject.harvest.utils.HtmlUtils;
import de.gerdiproject.json.datacite.Contributor;
import de.gerdiproject.json.datacite.DataCiteJson;
import de.gerdiproject.json.datacite.Date;
import de.gerdiproject.json.datacite.Description;
import de.gerdiproject.json.datacite.FundingReference;
import de.gerdiproject.json.datacite.GeoLocation;
import de.gerdiproject.json.datacite.Subject;
import de.gerdiproject.json.datacite.Title;
import de.gerdiproject.json.datacite.abstr.AbstractDate;
import de.gerdiproject.json.datacite.enums.ContributorType;
import de.gerdiproject.json.datacite.enums.DateType;
import de.gerdiproject.json.datacite.extension.generic.ResearchData;
import de.gerdiproject.json.datacite.extension.generic.WebLink;
import de.gerdiproject.json.datacite.extension.generic.enums.WebLinkType;


/**
 * This transformer parses metadata from a {@linkplain ClinicalTrialsVO}
 * and creates {@linkplain DataCiteJson} objects from it.
 *
 * @author Komal Ahir, Jan Frömberg
 */
public class ClinicalTrialsTransformer extends AbstractIteratorTransformer<ClinicalTrialsVO, DataCiteJson>
{
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final Calendar cal = Calendar.getInstance();


    @Override
    public void init(final AbstractETL<?, ?> etl)
    {
        // nothing to retrieve from the ETL
    }


    @Override
    public void clear()
    {
        // nothing to clean up
    }


    @Override
    protected DataCiteJson transformElement(final ClinicalTrialsVO vo) throws TransformerException
    {
        // create the document
        final Document viewPage = vo.getViewPage();
        final String nctId = HtmlUtils.getString(viewPage, ClinicalTrialsConstants.NCT_ID);
        final DataCiteJson document = new DataCiteJson(nctId);
        // add all possible metadata to the document
        document.setPublisher(ClinicalTrialsConstants.PROVIDER);
        document.setLanguage(ClinicalTrialsConstants.LANGUAGE);

        document.addTitles(getTitles(vo));
        document.addDescriptions(getDescriptions(vo));
        document.addDates(getDates(vo));
        document.addWebLinks(getWebLinkList(vo));
        document.addGeoLocations(getGeoLocations(vo));
        document.addResearchData(getResearchData(vo));

        document.addContributors(HtmlUtils.getObjects(viewPage, ClinicalTrialsConstants.OVERALL_CONTACT, this::parseContributor));
        document.addSubjects(HtmlUtils.getObjects(viewPage, ClinicalTrialsConstants.KEYWORD, this::parseSubject));
        document.addSubjects(HtmlUtils.getObjects(viewPage, ClinicalTrialsConstants.MESH_TERM, this::parseSubject));
        document.addSubjects(HtmlUtils.getObjects(viewPage, ClinicalTrialsConstants.OVERALL_STATUS, this::parseSubject));
        document.addFundingReferences(HtmlUtils.getObjectsFromParent(viewPage, ClinicalTrialsConstants.SPONSORS, this::parseFunder));

        document.setPublicationYear(getPublicationYear(vo));

        return document;
    }


    private Subject parseSubject(final Element ele)
    {
        return new Subject(ele.text(), null);
    }


    private Contributor parseContributor(final Element ele)
    {
        return new Contributor(ele.text(), ContributorType.ContactPerson);
    }


    private FundingReference parseFunder(final Element ele)
    {
        final String funderName = HtmlUtils.getString(ele, ClinicalTrialsConstants.AGENCY);
        return funderName == null ? null : new FundingReference(funderName);
    }


    private List<Title> getTitles(final ClinicalTrialsVO vo)
    {
        final List<Title> titleList = new LinkedList<>();

        // get the title
        final Element briefTitle = vo.getViewPage().selectFirst(ClinicalTrialsConstants.BRIEF_TITLE);
        final Element officialTitle = vo.getViewPage().selectFirst(ClinicalTrialsConstants.OFFICIAL_TITLE);

        // verify that there is data
        if (briefTitle != null)
            titleList.add(new Title(briefTitle.text()));

        if (officialTitle != null)
            titleList.add(new Title(officialTitle.text()));

        return titleList;
    }


    private List<Description> getDescriptions(final ClinicalTrialsVO vo)
    {
        final List<Description> descriptions = new LinkedList<>();
        // get the description
        final Element detailedDescription = vo.getViewPage().selectFirst(ClinicalTrialsConstants.DETAILED_DESCRIPTION);

        // verify that there is data
        if (detailedDescription != null)
            descriptions.add(new Description(detailedDescription.wholeText(), null));

        return descriptions;
    }


    private List<AbstractDate> getDates(final ClinicalTrialsVO vo)
    {
        final List<AbstractDate> dates = new LinkedList<>();
        // retrieve the dates
        final String submissionDate = HtmlUtils.getString(vo.getViewPage(), ClinicalTrialsConstants.STUDY_FIRST_SUBMITTED);
        final String firstPostedDate = HtmlUtils.getString(vo.getViewPage(), ClinicalTrialsConstants.STUDY_FIRST_POSTED);
        final String lastPostedDate = HtmlUtils.getString(vo.getViewPage(), ClinicalTrialsConstants.LAST_UPDATE_POSTED);

        // verify that there are dates
        if (submissionDate != null)
            dates.add(new Date(submissionDate, DateType.Submitted));

        if (firstPostedDate != null)
            dates.add(new Date(firstPostedDate, DateType.Available));

        if (lastPostedDate != null)
            dates.add(new Date(lastPostedDate, DateType.Updated));

        return dates;
    }


    private List<WebLink> getWebLinkList(final ClinicalTrialsVO vo)
    {
        final List<WebLink> webLinkList = new LinkedList<>();
        // retrieve the url, links and logo url
        final Elements linkElements = vo.getViewPage().select(ClinicalTrialsConstants.STUDY_RECORD_DETAIL_URL);

        for (final Element linkElement : linkElements) {
            final WebLink weblink = new WebLink(linkElement.text());
            weblink.setName(ClinicalTrialsUrlConstants.STUDY_RECORD_DETAIL);
            weblink.setType(WebLinkType.ViewURL);
            webLinkList.add(weblink);
        }

        webLinkList.add(ClinicalTrialsUrlConstants.LOGO_WEB_LINK);
        return webLinkList;
    }


    private List<GeoLocation> getGeoLocations(final ClinicalTrialsVO vo)
    {
        final List<GeoLocation> geoLocations = new LinkedList<>();
        // fetch all locations
        final Elements locationNames = vo.getViewPage().select(ClinicalTrialsConstants.COUNTRY);

        for (final Element locationName : locationNames) {
            final GeoLocation geolocation = new GeoLocation(locationName.text());
            geoLocations.add(geolocation);
        }

        return geoLocations;
    }


    private List<ResearchData> getResearchData(final ClinicalTrialsVO vo)
    {
        final List<ResearchData> researchDataList = new LinkedList<>();
        // fetch research data documents
        final Elements researchDataElements = vo.getViewPage().select(ClinicalTrialsConstants.VIEW_DOCUMENT_URL);

        for (final Element researchDataElement : researchDataElements) {
            final String fileExtension = researchDataElement.text();
            final String researchDataLabel = fileExtension.substring(fileExtension.lastIndexOf('.') + 1);
            final ResearchData researchData = new ResearchData(researchDataElement.text(), researchDataLabel);
            researchDataList.add(researchData);
        }

        return researchDataList;
    }


    private Integer getPublicationYear(final ClinicalTrialsVO vo)
    {
        try {
            final String dateString = HtmlUtils.getString(vo.getViewPage(), ClinicalTrialsConstants.STUDY_FIRST_POSTED);
            final java.util.Date parsedDate = dateFormat.parse(dateString);
            cal.setTime(parsedDate);

            return cal.get(Calendar.YEAR);

        } catch (ParseException e) {
            return null;
        }
    }
}
