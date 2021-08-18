(function () {
    'use strict';

    angular.module('theHiveControllers').controller('OrgLinksModalCtrl',
        function ($uibModalInstance, $filter, organisation, organisations, links, sharingProfiles) {
            var self = this;

            this.organisation = organisation;
            this.organisations = organisations;
            this.links = links;
            this.sharingProfiles = sharingProfiles;

            this.sharingProfilesNames = _.keys(this.sharingProfiles);
            this.allSelected = false;

            self.$onInit = function () {
                var linkedOrgMap = _.indexBy(self.organisation.links || [], 'toOrganisation');

                _.each(self.organisations, function (org) {
                    org.linked = false;

                    var found = _.find(self.links, function (link) {
                        return link.name === org.name;
                    });

                    org.linked = !!found;
                    org.linkDetails = linkedOrgMap[org.name] || {
                        toOrganisation: org.name,
                        linkType: 'default',
                        otherLinkType: 'default'
                    };
                });

                self.initialHash = self.hash = $filter('sha256')(self.getStateHash(self.organisations));
            };

            self.getStateHash = function () {
                return JSON.stringify(_.map(self.organisations, function (item) {
                    return {
                        name: item.name,
                        linked: item.linked,
                        linkType: item.linkDetails.linkType,
                        otherLinkType: item.linkDetails.otherLinkType
                    }
                }));
            };

            self.updateHash = function () {
                // Compute the dirty hash
                self.hash = $filter('sha256')(self.getStateHash(self.organisations));
            }

            self.toggleLink = function (org) {
                // Toogle the flag
                org.linked = !org.linked;

                // Compute the dirty hash
                self.updateHash();
            };

            self.toggleLinkAll = function () {
                _.each(this.organisations, function (item) {
                    item.linked = self.allSelected;
                });

                // Compute the dirty hash
                self.updateHash();
            };

            self.ok = function () {
                var newLinks;

                if (self.initialHash !== self.hash) {
                    newLinks = angular.copy(_.pluck(_.filter(this.organisations, function (item) {
                        return item.linked;
                    }), 'linkDetails'));
                }

                $uibModalInstance.close(newLinks);
            };

            this.cancel = function () {
                $uibModalInstance.dismiss('cancel');
            };

        });
})();
