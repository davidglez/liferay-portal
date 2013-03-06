package com.liferay.portal.lar.model;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.PortalDataContext;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.zip.ZipReader;
import com.liferay.portal.kernel.zip.ZipReaderFactoryUtil;
import com.liferay.portal.lar.PortalDataContextImpl;
import com.liferay.portal.lar.PortalDataHandlerImpl;
import com.liferay.portal.model.Organization;
import com.liferay.portal.model.User;
import com.liferay.portal.service.OrganizationLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;

import java.io.File;
import java.io.IOException;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


/**
 * Created with IntelliJ IDEA.
 * User: Liferay
 * Date: 2/13/13
 * Time: 12:10 PM
 * To change this template use File | Settings | File Templates.
 */
public class OrganizationPortalDataHandler extends PortalDataHandlerImpl {

	public File exportOrganizationAsFile(
			long companyId, long organizationId, long userId)
		throws PortalException, SystemException {

		PortalDataContext portalDataContext =
			new PortalDataContextImpl(companyId);

		Document document = SAXReaderUtil.createDocument();

		Element rootElement = document.addElement("root");

		Element headerElement = rootElement.addElement("header");

		headerElement.addAttribute(
			"build-number", String.valueOf(ReleaseInfo.getBuildNumber()));
		headerElement.addAttribute("export-date", Time.getRFC822());
		headerElement.addAttribute("type", "organization");

		User user = UserLocalServiceUtil.getUser(userId);

		Element userElement = rootElement.addElement("user");
		userElement.addAttribute("user-uuid", user.getUserUuid());

		Element organizationMapElement =
			rootElement.addElement("organization-map");

		Organization organization =
			OrganizationLocalServiceUtil.getOrganization(organizationId);

		Queue<Organization> organizations = new LinkedList<Organization>();
		organizations.add(organization);

		Organization currentOrganization;

		while(true){
			if(organizations.isEmpty()){
				break;
			}else{
				currentOrganization = organizations.remove();
			}
			exportOrganization("/organizations", currentOrganization,
				organizationMapElement, portalDataContext, organizations);
		}

		try {
			portalDataContext.addZipEntry(
				"/manifest.xml", document.formattedString());
		}
		catch (IOException e) {
			throw new PortalException();
		}

		return portalDataContext.getZipWriter().getFile();
	}

	protected void exportOrganization(
			String parentPath, Organization organization,
			Element parentElement, PortalDataContext portalDataContext,
			Queue<Organization> organizations)
		throws PortalException, SystemException {

		long organizationId = organization.getOrganizationId();
		long groupId = organization.getGroupId();
		long companyId = organization.getCompanyId();

		String path = parentPath
			.concat(StringPool.SLASH)
			.concat(String.valueOf(organizationId));

		Element element = parentElement.addElement("organization");

		Organization parentOrganization = organization.getParentOrganization();

		element.addAttribute("organization-id",
			String.valueOf(organization.getOrganizationId()));
		element.addAttribute("organization-uuid", organization.getUuid());
		element.addAttribute("path", path);

		String parentOrganizationUuid;
		if(parentOrganization == null){
			parentOrganizationUuid = null;
		}
		else{
			parentOrganizationUuid = String.valueOf(
				parentOrganization.getUuid());
		}
		element.addAttribute(
			"parent-organization-uuid", parentOrganizationUuid);

		List<Organization> suborganizations =
			OrganizationLocalServiceUtil.getOrganizations(
				companyId, organizationId);

		organizations.addAll(suborganizations);

		exportClassedModel(organization, path, portalDataContext);

		exportExpandoBridgeAttributes(organization, path, portalDataContext);

		exportPermissions(organization, groupId, portalDataContext);

		exportRolesByGroupId(path, groupId, portalDataContext);

	}

	public void importOrganization(long companyId, File file)
		throws PortalException, SystemException {

		ZipReader zipReader = ZipReaderFactoryUtil.getZipReader(file);

		PortalDataContext portalDataContext =
			new PortalDataContextImpl(companyId, zipReader);

		String xml = portalDataContext.getZipEntryAsString(
			"/manifest.xml");

		Element rootElement;

		try {
			Document document = SAXReaderUtil.read(xml);
			rootElement = document.getRootElement();
		}
		catch (DocumentException e) {
			throw new SystemException("It has not been able to read the file");
		}
		Element headerElement = rootElement.element("header");

		int buildNumber = ReleaseInfo.getBuildNumber();

		int importBuildNumber = GetterUtil.getInteger(
			headerElement.attributeValue("build-number"));

		if (buildNumber != importBuildNumber) {
			throw new PortalException(
				"The build number does not match");
		}

		String type = headerElement.attributeValue("type");

		if (!type.equals("organization")) {
			throw new PortalException(
				"The type of model does not correspond with an organization");
		}

		Element organizationElement =
			rootElement.element("organization-map");

		readPortalDataPermissions(portalDataContext);

		List<Element> organizationElements = organizationElement.elements();

		for (Element orgElement : organizationElements) {
			String path = orgElement.attributeValue("path");

			String parentOrganizationUuid =
				orgElement.attributeValue("parent-organization-uuid");
			String organizationUuid =
				orgElement.attributeValue("organization-uuid");
			long organizationId =
				Long.parseLong(
					orgElement.attributeValue("organization-id"));

			doImportOrganization(portalDataContext, path, rootElement,
				organizationId, organizationUuid, parentOrganizationUuid);
		}
	}

	protected void doImportOrganization(PortalDataContext portalDataContext,
			String path, Element rootElement,
			long organizationId, String organizationUuid,
			String parentOrganizationUuid)
		throws PortalException, SystemException {

		long companyId = portalDataContext.getCompanyId();

		Organization organization =
			(Organization)portalDataContext.getZipEntryAsObject(
				path + "/" + organizationId + ".xml");

		Element userElement = rootElement.element("user");
		String uuid = userElement.attributeValue("user-uuid");
		User user =
			UserLocalServiceUtil.getUserByUuidAndCompanyId(uuid, companyId);

		ServiceContext serviceContext = new ServiceContext();
		serviceContext.setUserId(user.getUserId());

		Organization parentOrganization =
			OrganizationLocalServiceUtil.fetchOrganization(
				parentOrganizationUuid);
		long parentOrganizationId = 0;
		if(parentOrganization != null){
			parentOrganizationId = parentOrganization.getOrganizationId();
			organization.setParentOrganizationId(parentOrganizationId);
		}

		Organization existingOrganization =
			OrganizationLocalServiceUtil.fetchOrganization(
				organizationUuid);

		Organization importedOrganization = null;

		setExpandoBrige(portalDataContext, serviceContext, path,
			Organization.class.getName());

		if (existingOrganization != null){
			importedOrganization =
				OrganizationLocalServiceUtil.updateOrganization(
					companyId, existingOrganization.getOrganizationId(),
					parentOrganizationId, organization.getName(),
					organization.getType(),organization.isRecursable(),
					organization.getRegionId(), organization.getCountryId(),
					organization.getStatusId(),	organization.getComments(),
					false, serviceContext);

			importRolesByGroupId(
				path, importedOrganization.getGroupId(), portalDataContext);

		}
		else {
			importedOrganization =
				OrganizationLocalServiceUtil.addOrganization(
					user.getUserId(), parentOrganizationId,
					organization.getName(), organization.getType(),
					organization.isRecursable(), organization.getRegionId(),
					organization.getCountryId(), organization.getStatusId(),
					organization.getComments(), false, serviceContext);

			importedOrganization.setUuid(organizationUuid);

			importedOrganization =
				OrganizationLocalServiceUtil.updateOrganization(
					importedOrganization);

			importRolesByGroupId(
				path, importedOrganization.getGroupId(), portalDataContext);
		}

		portalDataContext.importPermissions(
			organization, importedOrganization, "organization");

	}


}
