(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgLinksModalCtrl',
        function($uibModalInstance, $filter, organisation, organisations, links) {
            var self = this;

            this.organisation = organisation;
            this.organisations = organisations;
            this.links = links;
            this.allSelected = false;

            self.$onInit = function() {
                _.each(self.organisations, function(item) {
                    item.linked = false;

                    var found = _.find(self.links, function(link) {
                        return link.name === item.name;
                    });

                    item.linked = !!found;
                });

                self.initialHash = self.hash = $filter('sha256')(self.getStateHash(self.organisations));
            };

            self.getStateHash = function() {
                return JSON.stringify(_.map(self.organisations, function(item) {
                    return _.pick(item, 'name', 'linked');
                }));
            };

            self.toggleLink = function(org) {
                // Toogle the flag
                org.linked = !org.linked;

                // Compute the dirty hash
                self.hash = $filter('sha256')(self.getStateHash(self.organisations));
            };

            self.toggleLinkAll = function() {
                console.log(self.allSelected);

                _.each(this.organisations, function(item){
                    item.linked = self.allSelected;
                });

                self.hash = $filter('sha256')(self.getStateHash(self.organisations));
            };

            self.ok = function() {
                var newLinks;

                if(self.initialHash !== self.hash) {
                    newLinks = _.pluck(_.filter(this.organisations, function(item) {
                        return item.linked;
                    }), 'name');
                }

                $uibModalInstance.close(newLinks);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss('cancel');
            };

        });
})();
