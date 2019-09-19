(function() {
    'use strict';

    angular.module('theHiveControllers').controller('ProfileListCtrl',
        function(ProfileSrv) {
            var self = this;

            self.load = function() {
                ProfileSrv.list()
                    .then(function(response) {
                        self.list = response.data;
                    })
                    .catch(function() {
                        // TODO: Handle error
                    });
            };

            self.showProfile = function(/*mode, profile*/) {
                // TODO not yet implemented
            };

            self.load();
        });
})();
