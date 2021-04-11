(function () {
    'use strict';

    angular.module('theHiveComponents')
        .component('datalistHeader', {
            controller: function () { },
            controllerAs: '$ctrl',
            templateUrl: 'views/components/common/datalist-header.component.html',
            bindings: {
                title: '@',
                list: '<',
                total: '<'
            }
        });
})();
