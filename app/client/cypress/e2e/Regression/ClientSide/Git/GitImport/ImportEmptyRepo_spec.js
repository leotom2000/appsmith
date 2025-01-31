import homePage from "../../../../../locators/HomePage";
import gitSyncLocators from "../../../../../locators/gitSyncLocators";
import * as _ from "../../../../../support/Objects/ObjectsCore";

describe("Git import empty repository", { tags: ["@tag.Git"] }, function () {
  let repoName;
  const assertConnectFailure = true;
  const failureMessage =
    "git import failed. \nDetails: Cannot import app from an empty repo";
  before(() => {
    _.homePage.NavigateToHome();
    cy.createWorkspace();
    cy.wait("@createWorkspace").then((interception) => {
      const newWorkspaceName = interception.response.body.data.name;
      cy.CreateAppForWorkspace(newWorkspaceName, newWorkspaceName);
    });
    cy.generateUUID().then((uid) => {
      repoName = uid;
      _.gitSync.CreateTestGiteaRepo(repoName);
      //cy.createTestGithubRepo(repoName);
    });
  });

  it("1. Bug #12749 Git Import - Empty Repo NullPointerException", () => {
    cy.get(homePage.homeIcon).click();
    _.agHelper.GetNClick(homePage.createNew, 0);
    cy.get(homePage.workspaceImportAppOption).click({ force: true });
    cy.get(".t--import-json-card").next().click();
    cy.generateUUID().then((uid) => {
      repoName = uid;
      //cy.createTestGithubRepo(repoName);
      _.gitSync.CreateTestGiteaRepo(repoName);
      cy.importAppFromGit(repoName, true, failureMessage);
    });
    cy.get(gitSyncLocators.closeGitSyncModal).click();
  });
  after(() => {
    _.gitSync.DeleteTestGithubRepo(repoName);
    //cy.deleteTestGithubRepo(repoName);
  });
});
