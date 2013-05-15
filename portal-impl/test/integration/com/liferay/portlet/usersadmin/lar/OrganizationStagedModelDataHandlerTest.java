/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portlet.usersadmin.lar;

import com.liferay.portal.kernel.test.ExecutionTestListeners;
import com.liferay.portal.lar.BaseStagedModelDataHandlerTestCase;
import com.liferay.portal.model.Address;
import com.liferay.portal.model.EmailAddress;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.OrgLabor;
import com.liferay.portal.model.Organization;
import com.liferay.portal.model.OrganizationConstants;
import com.liferay.portal.model.PasswordPolicy;
import com.liferay.portal.model.PasswordPolicyRel;
import com.liferay.portal.model.Phone;
import com.liferay.portal.model.StagedModel;
import com.liferay.portal.model.Website;
import com.liferay.portal.service.AddressLocalServiceUtil;
import com.liferay.portal.service.EmailAddressLocalServiceUtil;
import com.liferay.portal.service.OrgLaborLocalServiceUtil;
import com.liferay.portal.service.OrganizationLocalServiceUtil;
import com.liferay.portal.service.PasswordPolicyRelLocalServiceUtil;
import com.liferay.portal.service.PhoneLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ServiceTestUtil;
import com.liferay.portal.service.WebsiteLocalServiceUtil;
import com.liferay.portal.test.LiferayIntegrationJUnitTestRunner;
import com.liferay.portal.test.MainServletExecutionTestListener;
import com.liferay.portal.test.TransactionalExecutionTestListener;
import com.liferay.portal.util.OrganizationTestUtil;
import com.liferay.portal.util.TestPropsValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.runner.RunWith;

/**
 * @author David Mendez Gonzalez
 */
@ExecutionTestListeners(
	listeners = {
		MainServletExecutionTestListener.class,
		TransactionalExecutionTestListener.class
	})
@RunWith(LiferayIntegrationJUnitTestRunner.class)
public class OrganizationStagedModelDataHandlerTest
	extends BaseStagedModelDataHandlerTestCase {

	@Override
	protected Map<String, List<StagedModel>> addDependentStagedModelsMap(
			Group group)
		throws Exception {

		Map<String, List<StagedModel>> dependentStagedModelsMap =
			new HashMap<String, List<StagedModel>>();

		Organization organization = OrganizationTestUtil.addOrganization();

		addDependentStagedModel(
			dependentStagedModelsMap, Organization.class, organization);

		return dependentStagedModelsMap;
	}

	@Override
	protected StagedModel addStagedModel(
			Group group,
			Map<String, List<StagedModel>> dependentStagedModelsMap)
		throws Exception {

		List<StagedModel> dependentStagedModels = dependentStagedModelsMap.get(
			Organization.class.getSimpleName());

		Organization organization = (Organization)dependentStagedModels.get(0);

		Organization parentOrganization = OrganizationTestUtil.addOrganization(
			OrganizationConstants.DEFAULT_PARENT_ORGANIZATION_ID,
			ServiceTestUtil.randomString(), false);

		organization.setParentOrganizationId(
			parentOrganization.getOrganizationId());

		OrganizationLocalServiceUtil.updateOrganization(organization);

		Address address = OrganizationTestUtil.addAddress(parentOrganization);
		addDependentStagedModel(
			dependentStagedModelsMap, Address.class, address);

		EmailAddress emailAddress = OrganizationTestUtil.addEmailAddress(
			parentOrganization);
		addDependentStagedModel(
			dependentStagedModelsMap, EmailAddress.class, emailAddress);

		OrgLabor orgLabor = OrganizationTestUtil.addOrgLabor(
			parentOrganization);
		_dependentOrgLabors.add(orgLabor);

		ServiceContext serviceContext = ServiceTestUtil.getServiceContext(
			group.getGroupId(), TestPropsValues.getUserId());
		PasswordPolicy passwordPolicy =
			OrganizationTestUtil.addPasswordPolicyRel(
				parentOrganization, serviceContext);
		addDependentStagedModel(
			dependentStagedModelsMap, PasswordPolicy.class, passwordPolicy);

		Phone phone = OrganizationTestUtil.addPhone(parentOrganization);
		addDependentStagedModel(dependentStagedModelsMap, Phone.class, phone);

		Website website = OrganizationTestUtil.addWebsite(parentOrganization);
		addDependentStagedModel(
			dependentStagedModelsMap, Website.class, website);

		return parentOrganization;
	}

	@Override
	protected StagedModel getStagedModel(String uuid, Group group) {
		try {
			return OrganizationLocalServiceUtil.
				fetchOrganizationByUuidAndCompanyId(uuid, group.getCompanyId());
		}
		catch (Exception e) {
			return null;
		}
	}

	@Override
	protected Class<? extends StagedModel> getStagedModelClass() {
		return Organization.class;
	}

	@Override
	protected void validateImport(
			StagedModel stagedModel,
			Map<String, List<StagedModel>> dependentStagedModelsMap,
			Group group)
		throws Exception {

		List<StagedModel> dependentOrganizationStagedModels =
			dependentStagedModelsMap.get(Organization.class.getSimpleName());

		Assert.assertEquals(1, dependentOrganizationStagedModels.size());

		Organization organization =
			(Organization)dependentOrganizationStagedModels.get(0);

		Assert.assertNotNull(
			OrganizationLocalServiceUtil.fetchOrganizationByUuidAndCompanyId(
				organization.getUuid(), group.getCompanyId()));

		Organization parentOrganization = (Organization)stagedModel;

		// Address t<StagedModel> dependentAddressStagedModels =

		LisdependentStagedModelsMap.get(Address.class.getSimpleName());

		Assert.assertEquals(1, dependentAddressStagedModels.size());

		Address address = (Address)dependentAddressStagedModels.get(0);

		Assert.assertNotNull(
			AddressLocalServiceUtil.fetchAddressByUuidAndCompanyId(
				address.getUuid(), group.getCompanyId()));
		Assert.assertEquals(
			parentOrganization.getPrimaryKey(), address.getClassPK());

		// EmailAddress t<StagedModel> dependentEmailAddressStagedModels =

		LisdependentStagedModelsMap.get(EmailAddress.class.getSimpleName());

		Assert.assertEquals(1, dependentEmailAddressStagedModels.size());
		EmailAddress emailAddress =
			(EmailAddress)dependentEmailAddressStagedModels.get(0);

		Assert.assertNotNull(
			EmailAddressLocalServiceUtil.fetchEmailAddressByUuidAndCompanyId(
				emailAddress.getUuid(), group.getCompanyId()));
		Assert.assertEquals(
			parentOrganization.getPrimaryKey(), emailAddress.getClassPK());

		// OrgLabor t<OrgLabor> orgLabors =

		LisOrgLaborLocalServiceUtil.getOrgLabors(
			parentOrganization.getOrganizationId());
		Assert.assertEquals(
			orgLabors.get(0).getOrganizationId(),
			_dependentOrgLabors.get(0).getOrganizationId());

		// PasswordPolicyRel t<StagedModel> dependentPasswordPolicyStagedModels

		Lis= dependentStagedModelsMap.get(PasswordPolicy.class.getSimpleName());

		Assert.assertEquals(1, dependentPasswordPolicyStagedModels.size());
		PasswordPolicy passwordPolicy =
			(PasswordPolicy)dependentPasswordPolicyStagedModels.get(0);

		PasswordPolicyRel passwordPolicyRel =
			PasswordPolicyRelLocalServiceUtil.fetchPasswordPolicyRel(
				parentOrganization.getModelClassName(),
				parentOrganization.getPrimaryKey());
		Assert.assertEquals(
			passwordPolicy.getPasswordPolicyId(),
			passwordPolicyRel.getPasswordPolicyId());

		// Phone t<StagedModel> dependentPhoneStagedModels =

		LisdependentStagedModelsMap.get(Phone.class.getSimpleName());

		Assert.assertEquals(1, dependentPhoneStagedModels.size());
		Phone phone = (Phone)dependentPhoneStagedModels.get(0);

		Assert.assertNotNull(
			PhoneLocalServiceUtil.fetchPhoneByUuidAndCompanyId(
				phone.getUuid(), group.getCompanyId()));
		Assert.assertEquals(
			parentOrganization.getPrimaryKey(), phone.getClassPK());

		// Website t<StagedModel> dependentWebsiteStagedModels =

		LisdependentStagedModelsMap.get(Website.class.getSimpleName());

		Assert.assertEquals(1, dependentWebsiteStagedModels.size());
		Website website = (Website)dependentWebsiteStagedModels.get(0);

		Assert.assertNotNull(
			WebsiteLocalServiceUtil.fetchWebsiteByUuidAndCompanyId(
				website.getUuid(), group.getCompanyId()));
		Assert.assertEquals(
			parentOrganization.getPrimaryKey(), website.getClassPK());
	}

	private List<OrgLabor> _dependentOrgLabors = new ArrayList<OrgLabor>();

}