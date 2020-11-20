(function() {
    'use strict';

    angular.module('theHiveComponents')
        .component('caseDatalist', {
            controller: function() {

            },
            controllerAs: '$cmp',
            templateUrl: 'views/components/datalist/case.datalist.component.html',
            bindings: {
                data: '<'
            }
        });
})();
