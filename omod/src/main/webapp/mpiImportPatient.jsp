<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<spring:htmlEscape defaultHtmlEscape="true" />
<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/index.htm" />
<openmrs:htmlInclude file="/scripts/jquery/dataTables/css/dataTables_jui.css"/>
<openmrs:htmlInclude file="/scripts/jquery/dataTables/js/jquery.dataTables.min.js"/>
<openmrs:htmlInclude file="/scripts/jquery-ui/js/openmrsSearch.js" />

<h2>Import MPI Patient</h2>
<p>The remote demographic record will be imported into OpenMRS as the following demographic record:</p>

<form id="importForm" modelAttribute="importPatient" method="post"
			enctype="multipart/form-data">
			
			
<div id="patientHeaderPatientName">
  <c:out value="${patient.personName}" />
</div>
<div id="patientHeaderPreferredIdentifier">
  <c:if test="${fn:length(patient.activeIdentifiers) > 0}">
    <c:forEach var="identifier" items="${patient.activeIdentifiers}"
			begin="0" end="0">
      <span class="patientHeaderPatientIdentifier">
        <span
				id="patientHeaderPatientIdentifierType">
          ${identifier.identifierType.name}<openmrs:extensionPoint
						pointId="org.openmrs.patientDashboard.afterPatientHeaderPatientIdentifierType"
						type="html"
						parameters="identifierLocation=${identifier.location.name}" />:
        </span> ${identifier.identifier}
      </span>
    </c:forEach>
  </c:if>
</div>
<table id="patientHeaderGeneralInfo">
  <tr class="patientHeaderGeneralInfoRow">
    <td id="patientHeaderPatientGender">
      <c:if
				test="${patient.gender == 'M'}">
        <img src="${pageContext.request.contextPath}/images/male.gif"
					alt=''<openmrs:message code="Person.gender.male"/>'
        id="maleGenderIcon" />
      </c:if>
      <c:if test="${patient.gender == 'F'}">
        <img src="${pageContext.request.contextPath}/images/female.gif"
					alt=''<openmrs:message code="Person.gender.female"/>'
        id="femaleGenderIcon" />
      </c:if>
    </td>
    <td id="patientHeaderPatientAge">
      <openmrs:extensionPoint
				pointId="org.openmrs.patientDashboard.beforePatientHeaderPatientAge"
				type="html" parameters="patientId=${patient.patientId}" />
      <c:if
				test="${patient.age > 0}">
        ${patient.age} <openmrs:message
					code="Person.age.years" />
      </c:if>
      <c:if test="${patient.age == 0}">
        < 1
        <openmrs:message
					code="Person.age.year" />
      </c:if>
      <span id="patientHeaderPatientBirthdate">
        <c:if
					test="${not empty patient.birthdate}">
          (<c:if
           test="${patient.birthdateEstimated}">~</c:if>
          <openmrs:formatDate date="${patient.birthdate}" type="medium" />)
        </c:if>
        <c:if test="${empty patient.birthdate}">
          <openmrs:message code="Person.age.unknown" />
        </c:if>
      </span>
    </td>

    <%-- Display selected person attributes from the manage person attributes page --%>
    <openmrs:forEachDisplayAttributeType personType="patient"
			displayType="header" var="attrType">
      <td class="patientHeaderPersonAttribute">
        <openmrs:message
					code="PersonAttributeType.${fn:replace(attrType.name, ' ', '')}"
					text="${attrType.name}" />: <b>${patient.attributeMap[attrType.name]}</b>
      </td>
    </openmrs:forEachDisplayAttributeType>

    <%-- The following is kept for backward compatibility. --%>
    <td id="patientHeaderPatientTribe">
      <openmrs:extensionPoint
				pointId="org.openmrs.patientDashboard.afterPatientHeaderPatientTribe"
				type="html" parameters="patientId=${patient.patientId}" />
    </td>
    <openmrs:globalProperty key="use_patient_attribute.healthCenter"
			defaultValue="false" var="showHealthCenter" />
    <c:if
			test="${showHealthCenter && not empty patient.attributeMap['Health Center']}">
      <td id="patientHeaderHealthCenter">
        <openmrs:message
					code="PersonAttributeType.HealthCenter" />: <b>${patient.attributeMap['Health Center']}</b>
      </td>
    </c:if>
    <td id="patientDashboardHeaderExtension">
      <openmrs:extensionPoint
				pointId="org.openmrs.patientDashboard.Header" type="html"
				parameters="patientId=${patient.patientId}" />
    </td>
    <td style="width: 100%;" class="patientHeaderEmptyData">&nbsp;</td>
    <td id="patientHeaderOtherIdentifiers">
      <c:if
				test="${fn:length(patient.activeIdentifiers) > 1}">
        <c:forEach var="identifier"
					items="${patient.activeIdentifiers}" begin="1" end="1">
          <span class="patientHeaderPatientIdentifier">
            ${identifier.identifierType.name}<openmrs:extensionPoint
							pointId="org.openmrs.patientDashboard.afterPatientHeaderPatientIdentifierType"
							type="html"
							parameters="identifierLocation=${identifier.location.name}" />:
            ${identifier.identifier}
          </span>
        </c:forEach>
      </c:if>
      <c:if test="${fn:length(patient.activeIdentifiers) > 2}">
        <div id="patientHeaderMoreIdentifiers">
          <c:forEach var="identifier"
						items="${patient.activeIdentifiers}" begin="2">
            <span class="patientHeaderPatientIdentifier">
              ${identifier.identifierType.name}<openmrs:extensionPoint
								pointId="org.openmrs.patientDashboard.afterPatientHeaderPatientIdentifierType"
								type="html"
								parameters="identifierLocation=${identifier.location.name}" />:
              ${identifier.identifier}
            </span>
          </c:forEach>
        </div>
      </c:if>
    </td>
    <c:if test="${fn:length(patient.activeIdentifiers) > 2}">
      <td width="32" class="patientHeaderShowMoreIdentifiersData">
        <small>
          <a
					id="patientHeaderShowMoreIdentifiers"
					onclick="return showMoreIdentifiers()"
					title=''
            <openmrs:message code="patientDashboard.showMoreIdentifers"/>'><openmrs:message
							code="general.nMore"
							arguments="${fn:length(patient.activeIdentifiers) - 2}" />
          </a>
        </small>
      </td>
    </c:if>
  </tr>
</table>

	<div class="boxHeader"><openmrs:message code="Patient.title"/></div>
	<div class="box$">
		<table class="personName">
			<thead>
				<tr class="patientDemographicsHeaderRow">
					<th class="patientDemographicsPersonNameHeader"><openmrs:message code="Person.names"/></th>
					<openmrs:forEachDisplayAttributeType personType="patient" displayType="viewing" var="attrType">
						<th class="patientDemographicsPersonAttTypeHeader"><openmrs:message code="PersonAttributeType.${fn:replace(attrType.name, ' ', '')}" text="${attrType.name}"/></th>
					</openmrs:forEachDisplayAttributeType>
				</tr>
			</thead>
			<tbody>
				<tr class="patientDemographicsRow">
					<td valign="top" class="patientDemographicsData" >
						<c:forEach var="name" items="${patient.names}" varStatus="status">
							<c:if test="${!name.voided}">
								<% request.setAttribute("name", pageContext.getAttribute("name")); %>
								<spring:nestedPath path="name">
									<openmrs:portlet url="nameLayout" id="namePortlet" size="quickView" parameters="layoutShowExtended=true" />
								</spring:nestedPath>
							</c:if>
						</c:forEach>
					</td>
					<openmrs:forEachDisplayAttributeType personType="patient" displayType="viewing" var="attrType">
						<td valign="top" class="patientDemographicsAttrName">${patient.attributeMap[attrType.name]}</td>
					</openmrs:forEachDisplayAttributeType>
				</tr>
			</tbody>
		</table>
	</div>
	
	<br/>
	
	<div class="boxHeader"><openmrs:message code="Person.addresses"/></div>
	<div class="box">
		<table class="personAddress">
			<thead>
				<openmrs:portlet url="addressLayout" id="addressPortlet" size="columnHeaders" parameters="layoutShowTable=false|layoutShowExtended=true" />
			</thead>
			<tbody>
				<c:forEach var="address" items="${patient.addresses}" varStatus="status">
					<c:if test="${!address.voided}">
					<% request.setAttribute("address", pageContext.getAttribute("address")); %>
					<spring:nestedPath path="address">
						<openmrs:portlet url="addressLayout" id="addressPortlet" size="inOneRow" parameters="layoutMode=view|layoutShowTable=false|layoutShowExtended=true" />
					</spring:nestedPath>
					</c:if>
				</c:forEach>
			</tbody>
		</table>
	</div>
	<p>If you're satisfied with the data provided click the "import" button below.</p>
	<br /> <input type="submit" value="Import Patient" style="float:right"> <br />
</form>


<%@ include file="/WEB-INF/template/footer.jsp"%>
