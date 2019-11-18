(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('sharingList', {
            controller: function() {
                this.remove = function(share) {
                    this.onDelete({
                        share: share
                    });
                };
            },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/sharing/sharing-list.html',
            bindings: {
                shares: '<',
                organisations: '<',
                profiles: '<',
                readOnly: '<',
                //onReload: '&',
                onDelete: '&'
            }
        });
})();
