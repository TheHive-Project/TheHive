(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('sharingList', {
            controller: function() {
                this.remove = function(id) {
                    this.onDelete({
                        id: id
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
