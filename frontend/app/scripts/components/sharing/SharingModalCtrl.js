(function() {
    'use strict';

    angular.module('theHiveControllers')
        .controller('SharingModalCtrl', function($uibModalInstance, shares) {
            var self = this;

            this.shares = shares || [];
            this.selectAll = false;

            this.toggleAll = function() {
                _.each(this.shares, function(item) {
                    item.selected = self.selectAll;
                });
            };

            this.save = function() {
                var selection = _.filter(this.shares, function(item) {
                    return item.selected;
                });

                $uibModalInstance.close(_.pluck(selection, 'organisationName'));
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        });
})();
